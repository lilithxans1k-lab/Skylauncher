// The SkyLaunch in-game client (menu on Right Shift, HUD editor, SkyBlock HUD).
// Its jars are built from client-mod/ and shipped inside the launcher; this copies the right
// one into each profile's mods folder.
const fs = require('fs');
const path = require('path');
const paths = require('./paths');

const FILE = 'skylaunch-client.jar';

/** Which client build fits which profile. */
const BUILDS = [
  { loader: 'fabric', mcVersion: '26.2', jar: 'skylaunch-client-fabric.jar' },
  { loader: 'forge', mcVersion: '1.8.9', jar: 'skylaunch-client-forge-1.8.9.jar' },
];

/** Minecraft version modern presets prefer, because the client mod is built for it. */
const PREFERRED_MODERN = '26.2';

let resourcesDir = null;

function init(dir) {
  resourcesDir = dir;
}

function buildFor(inst) {
  return BUILDS.find((b) => b.loader === inst.loader && b.mcVersion === inst.mcVersion) || null;
}

function jarPath(build) {
  if (!resourcesDir || !build) return null;
  const p = path.join(resourcesDir, 'client', build.jar);
  return fs.existsSync(p) ? p : null;
}

/**
 * Status for the UI: 'on' | 'off' | 'wrong-version' | 'not-built'.
 */
function status(inst) {
  const build = buildFor(inst);
  if (!build) return 'wrong-version';
  if (!jarPath(build)) return 'not-built';
  return inst.clientMod === false ? 'off' : 'on';
}

/** Put the client jar in (or take it out of) the profile's mods folder to match its setting. */
function sync(inst) {
  const dir = paths.modsDir(inst.id);
  fs.mkdirSync(dir, { recursive: true });
  const dest = path.join(dir, FILE);
  const src = jarPath(buildFor(inst));
  const want = src && inst.clientMod !== false;
  if (want) {
    const same = fs.existsSync(dest) && fs.statSync(dest).size === fs.statSync(src).size
      && fs.statSync(dest).mtimeMs >= fs.statSync(src).mtimeMs;
    if (!same) fs.copyFileSync(src, dest);
  } else {
    fs.rmSync(dest, { force: true });
  }
  fs.rmSync(`${dest}.disabled`, { force: true });
}

module.exports = { init, sync, status, buildFor, FILE, PREFERRED_MODERN, BUILDS };
