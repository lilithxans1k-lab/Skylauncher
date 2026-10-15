// Small HTTP helpers on top of Node's built-in fetch.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { Readable } = require('stream');
const { pipeline } = require('stream/promises');

const pkg = require('../../package.json');
const USER_AGENT = `SkyLaunch/${pkg.version} (Hypixel SkyBlock launcher)`;

async function getJson(url, { retries = 2 } = {}) {
  let lastErr;
  for (let i = 0; i <= retries; i++) {
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
        signal: AbortSignal.timeout(20000),
      });
      if (res.status === 404) {
        const err = new Error(`Not found: ${url}`);
        err.status = 404;
        throw err;
      }
      if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
      return await res.json();
    } catch (e) {
      lastErr = e;
      if (e.status === 404) throw e;
      await new Promise((r) => setTimeout(r, 500 * (i + 1)));
    }
  }
  throw lastErr;
}

function sha1File(file) {
  return new Promise((resolve, reject) => {
    const h = crypto.createHash('sha1');
    fs.createReadStream(file).on('data', (d) => h.update(d)).on('end', () => resolve(h.digest('hex'))).on('error', reject);
  });
}

/**
 * Download url to dest. Writes to a .part file first so a cancelled download never
 * leaves a broken jar behind. If sha1 is given and the file already matches, skips the download.
 * onProgress(receivedBytes, totalBytes) is optional.
 */
async function download(url, dest, { sha1, onProgress } = {}) {
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  if (sha1 && fs.existsSync(dest) && (await sha1File(dest)) === sha1) return dest;

  const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT }, redirect: 'follow' });
  if (!res.ok || !res.body) throw new Error(`Download failed (HTTP ${res.status}): ${url}`);
  const total = Number(res.headers.get('content-length')) || 0;
  let received = 0;
  const tmp = `${dest}.part`;
  const body = Readable.fromWeb(res.body);
  if (onProgress) body.on('data', (c) => { received += c.length; onProgress(received, total); });
  await pipeline(body, fs.createWriteStream(tmp));

  if (sha1) {
    const got = await sha1File(tmp);
    if (got !== sha1) {
      fs.rmSync(tmp, { force: true });
      throw new Error(`Checksum mismatch for ${path.basename(dest)} — the download was corrupted, try again.`);
    }
  }
  fs.renameSync(tmp, dest);
  return dest;
}

module.exports = { getJson, download, sha1File, USER_AGENT };
