// Finds the Java version a Minecraft version needs and downloads it (Eclipse Temurin)
// into the launcher's own folder, so players never have to install Java themselves.
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const AdmZip = require('adm-zip');
const paths = require('./paths');
const { getJson, download } = require('./net');

const MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
let manifestCache = null;
const vanillaCache = new Map();

async function vanillaManifest() {
  if (!manifestCache) manifestCache = await getJson(MANIFEST);
  return manifestCache;
}

async function vanillaVersionJson(mcVersion) {
  if (vanillaCache.has(mcVersion)) return vanillaCache.get(mcVersion);
  const m = await vanillaManifest();
  const entry = m.versions.find((v) => v.id === mcVersion);
  if (!entry) throw new Error(`Minecraft ${mcVersion} isn't in Mojang's version list.`);
  const json = await getJson(entry.url);
  vanillaCache.set(mcVersion, json);
  return json;
}

/** Java major version Mojang says this Minecraft version needs (1.8.9 has none listed → 8). */
async function requiredJavaMajor(mcVersion) {
  const json = await vanillaVersionJson(mcVersion);
  return (json.javaVersion && json.javaVersion.majorVersion) || 8;
}

function platform() {
  const os = { win32: 'windows', darwin: 'mac', linux: 'linux' }[process.platform];
  const arch = { x64: 'x64', arm64: 'aarch64' }[process.arch] || 'x64';
  return { os, arch };
}

function findJavaBinary(dir) {
  const exe = process.platform === 'win32' ? 'javaw.exe' : 'java';
  const stack = [dir];
  while (stack.length) {
    const d = stack.pop();
    let entries;
    try { entries = fs.readdirSync(d, { withFileTypes: true }); } catch { continue; }
    for (const e of entries) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) stack.push(p);
      else if (e.name === exe && path.basename(d) === 'bin') return p;
    }
  }
  return null;
}

// Temurin publishes LTS lines; if Mojang asks for a non-LTS major, use the next one up.
function candidateMajors(major) {
  const lts = [8, 11, 17, 21, 25];
  return [major, ...lts.filter((v) => v > major)].filter((v, i, a) => a.indexOf(v) === i);
}

function extractTarGz(file, dest) {
  return new Promise((resolve, reject) => {
    execFile('tar', ['-xzf', file, '-C', dest], (err) => (err ? reject(err) : resolve()));
  });
}

/**
 * Returns a path to a Java executable for `major`, downloading it first if needed.
 * onProgress(label, fraction) is optional.
 */
async function ensureJava(major, onProgress = () => {}) {
  const { os, arch } = platform();
  for (const m of candidateMajors(major)) {
    const dir = path.join(paths.runtimeDir(), `java-${m}`);
    const existing = fs.existsSync(dir) && findJavaBinary(dir);
    if (existing) return existing;

    for (const image of ['jre', 'jdk']) {
      const url = `https://api.adoptium.net/v3/binary/latest/${m}/ga/${os}/${arch}/${image}/hotspot/normal/eclipse`;
      const archive = path.join(paths.cacheDir(), `temurin-${m}-${image}.${os === 'windows' ? 'zip' : 'tar.gz'}`);
      try {
        await download(url, archive, {
          onProgress: (r, t) => onProgress(`Downloading Java ${m}`, t ? r / t : 0),
        });
      } catch {
        continue; // try the next image type or major
      }
      onProgress(`Unpacking Java ${m}`, 1);
      fs.rmSync(dir, { recursive: true, force: true });
      fs.mkdirSync(dir, { recursive: true });
      if (archive.endsWith('.zip')) new AdmZip(archive).extractAllTo(dir, true);
      else await extractTarGz(archive, dir);
      fs.rmSync(archive, { force: true });
      const bin = findJavaBinary(dir);
      if (bin) {
        if (process.platform !== 'win32') fs.chmodSync(bin, 0o755);
        return bin;
      }
    }
  }
  throw new Error(`Couldn't download Java ${major}. Check your internet connection, or set a Java path in Settings.`);
}

module.exports = { ensureJava, requiredJavaMajor, vanillaVersionJson, vanillaManifest };
