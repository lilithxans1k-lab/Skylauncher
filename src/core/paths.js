// Where everything lives on disk. On Windows this is %APPDATA%\SkyLaunch.
const path = require('path');
const fs = require('fs');

let base = null;

function init(userDataDir) {
  base = userDataDir;
  for (const p of [root(), instancesDir(), runtimeDir(), cacheDir()]) {
    fs.mkdirSync(p, { recursive: true });
  }
}

function ensureInit() {
  if (!base) throw new Error('paths.init() must be called before use');
  return base;
}

// Shared game files (versions, libraries, assets) — downloaded once, used by every profile.
const root = () => path.join(ensureInit(), 'minecraft');
// One folder per profile; each is its own game directory with its own mods, config and saves.
const instancesDir = () => path.join(ensureInit(), 'instances');
const instanceDir = (id) => path.join(instancesDir(), id);
const modsDir = (id) => path.join(instanceDir(id), 'mods');
// Java runtimes the launcher downloads for itself.
const runtimeDir = () => path.join(ensureInit(), 'runtime');
const cacheDir = () => path.join(ensureInit(), 'cache');
const settingsFile = () => path.join(ensureInit(), 'settings.json');
const accountsFile = () => path.join(ensureInit(), 'accounts.json');

module.exports = {
  init, root, instancesDir, instanceDir, modsDir, runtimeDir, cacheDir, settingsFile, accountsFile,
};
