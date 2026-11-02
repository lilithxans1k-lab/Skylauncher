// Installs mod loaders into the shared game folder.
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const paths = require('./paths');
const { getJson, download, USER_AGENT } = require('./net');
const { FORGE_189 } = require('./presets');

const FABRIC_META = 'https://meta.fabricmc.net/v2';
const NEOFORGE_MAVEN = 'https://maven.neoforged.net/releases/net/neoforged/neoforge';

/** Newest stable Fabric Loader version for a Minecraft version. */
async function latestFabricLoader(mcVersion) {
  const list = await getJson(`${FABRIC_META}/versions/loader/${encodeURIComponent(mcVersion)}`);
  if (!list.length) throw new Error(`Fabric doesn't support Minecraft ${mcVersion} yet.`);
  const stable = list.find((e) => e.loader.stable) || list[0];
  return stable.loader.version;
}

/**
 * Writes Fabric's version JSON into versions/<id>/<id>.json, which the launch library
 * picks up through `version.custom`. Returns the id.
 */
async function ensureFabric(mcVersion, loaderVersion) {
  const loader = loaderVersion || (await latestFabricLoader(mcVersion));
  const profile = await getJson(
    `${FABRIC_META}/versions/loader/${encodeURIComponent(mcVersion)}/${encodeURIComponent(loader)}/profile/json`,
  );
  const dir = path.join(paths.root(), 'versions', profile.id);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, `${profile.id}.json`), JSON.stringify(profile, null, 2));
  return { customId: profile.id, loaderVersion: loader };
}

/** Downloads the Forge 1.8.9 universal jar. Returns its path. */
async function ensureForge189() {
  const dest = path.join(paths.root(), 'loaders', `forge-${FORGE_189.mcVersion}-${FORGE_189.forgeVersion}-universal.jar`);
  if (!fs.existsSync(dest)) await download(FORGE_189.universalUrl, dest);
  return { forgeJar: dest, loaderVersion: FORGE_189.forgeVersion };
}

// --- NeoForge ---------------------------------------------------------------
// NeoForge numbers its builds after the Minecraft version: 21.1.x is for 1.21.1, 21.0.x for
// 1.21, 20.4.x for 1.20.4. From Minecraft 26.1 on it uses the game version directly
// (26.1.0.x is for 26.1).

let neoforgeListCache = null;

/** Every published NeoForge version, oldest first, read from NeoForge's maven. */
async function neoforgeVersions() {
  if (neoforgeListCache) return neoforgeListCache;
  const res = await fetch(`${NEOFORGE_MAVEN}/maven-metadata.xml`, {
    headers: { 'User-Agent': USER_AGENT },
    signal: AbortSignal.timeout(20000),
  });
  if (!res.ok) throw new Error(`Couldn't reach NeoForge's servers (HTTP ${res.status}).`);
  const xml = await res.text();
  neoforgeListCache = [...xml.matchAll(/<version>([^<]+)<\/version>/g)].map((m) => m[1]);
  return neoforgeListCache;
}

/** Minecraft version a NeoForge version is built for, or null if it can't tell. */
function neoforgeGameVersion(nfVersion) {
  const m = nfVersion.match(/^(\d+)\.(\d+)\.(\d+)/);
  if (!m) return null;
  const [a, b, c] = m.slice(1).map(Number);
  if (a >= 26) return c ? `${a}.${b}.${c}` : `${a}.${b}`;
  if (a === 20 && b === 1) return null; // 1.20.1 lives on the old "forge" artifact, not this one
  return b ? `1.${a}.${b}` : `1.${a}`;
}

/** Minecraft versions NeoForge supports, newest first. */
async function neoforgeGameVersions() {
  const set = new Set((await neoforgeVersions()).map(neoforgeGameVersion).filter(Boolean));
  const { sortNewestFirst } = require('./modrinth');
  return sortNewestFirst([...set]);
}

/** Newest NeoForge build for a Minecraft version, preferring stable over beta. */
async function latestNeoForge(mcVersion) {
  const all = (await neoforgeVersions()).filter((v) => neoforgeGameVersion(v) === mcVersion);
  if (!all.length) throw new Error(`NeoForge doesn't support Minecraft ${mcVersion}.`);
  const stable = all.filter((v) => !/beta|alpha/i.test(v));
  return (stable.length ? stable : all)[(stable.length ? stable : all).length - 1];
}

// The installer wants a console Java, not javaw.exe, so its output reaches the log.
function consoleJava(javaPath) {
  if (process.platform !== 'win32') return javaPath;
  const java = path.join(path.dirname(javaPath), 'java.exe');
  return fs.existsSync(java) ? java : javaPath;
}

function runJava(javaPath, args, log) {
  return new Promise((resolve) => {
    const child = execFile(consoleJava(javaPath), args, { windowsHide: true, maxBuffer: 64 * 1024 * 1024 }, (err) => resolve(!err));
    const out = (d) => String(d).split(/\r?\n/).filter(Boolean).forEach((l) => log(`[NeoForge installer] ${l}`));
    child.stdout.on('data', out);
    child.stderr.on('data', out);
  });
}

/**
 * Installs NeoForge into the shared game folder by running its official installer, which
 * downloads its libraries and patches Minecraft. Writes versions/neoforge-<v>/neoforge-<v>.json
 * for the launch library's `version.custom`. Only runs the installer the first time.
 */
async function ensureNeoForge(mcVersion, loaderVersion, javaPath, { progress = () => {}, log = () => {} } = {}) {
  const nf = loaderVersion || (await latestNeoForge(mcVersion));
  const customId = `neoforge-${nf}`;
  const versionJson = path.join(paths.root(), 'versions', customId, `${customId}.json`);
  if (fs.existsSync(versionJson)) return { customId, loaderVersion: nf };

  progress(`Downloading NeoForge ${nf}`, 0);
  const installer = path.join(paths.root(), 'loaders', `neoforge-${nf}-installer.jar`);
  if (!fs.existsSync(installer)) {
    await download(`${NEOFORGE_MAVEN}/${nf}/neoforge-${nf}-installer.jar`, installer, {
      onProgress: (r, t) => progress(`Downloading NeoForge ${nf}`, t ? r / t : 0),
    });
  }

  // The installer refuses to run without a vanilla launcher profile file.
  const profiles = path.join(paths.root(), 'launcher_profiles.json');
  if (!fs.existsSync(profiles)) fs.writeFileSync(profiles, JSON.stringify({ profiles: {} }));

  progress(`Installing NeoForge ${nf} (first time only, this takes a minute)`, 1);
  const ok = await runJava(javaPath, ['-jar', installer, '--installClient', paths.root()], log)
    || await runJava(javaPath, ['-jar', installer, '--install-client', paths.root()], log);
  if (!ok || !fs.existsSync(versionJson)) {
    throw new Error(`NeoForge ${nf} didn't install. Open the Logs tab for the installer's output.`);
  }
  return { customId, loaderVersion: nf };
}

/**
 * JVM arguments from a NeoForge version JSON (module path, add-opens and so on), with the
 * placeholders filled in. The launch library only reads game arguments from custom versions,
 * so these have to be passed in ourselves.
 */
function neoforgeJvmArgs(customId) {
  const file = path.join(paths.root(), 'versions', customId, `${customId}.json`);
  const json = JSON.parse(fs.readFileSync(file, 'utf8'));
  const raw = (json.arguments && json.arguments.jvm) || [];
  const fields = {
    '${library_directory}': path.join(paths.root(), 'libraries'),
    '${classpath_separator}': process.platform === 'win32' ? ';' : ':',
    '${version_name}': customId,
  };
  return raw
    .filter((a) => typeof a === 'string') // skip OS-specific rule objects; vanilla's are handled already
    .map((a) => a.replace(/\$\{[a-z_]+\}/g, (k) => (k in fields ? fields[k] : k)));
}

module.exports = {
  ensureFabric, ensureForge189, latestFabricLoader,
  ensureNeoForge, neoforgeJvmArgs, neoforgeGameVersions, neoforgeGameVersion, latestNeoForge,
};
