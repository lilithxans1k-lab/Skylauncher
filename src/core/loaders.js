// Installs mod loaders into the shared game folder.
const fs = require('fs');
const path = require('path');
const paths = require('./paths');
const { getJson, download } = require('./net');
const { FORGE_189 } = require('./presets');

const FABRIC_META = 'https://meta.fabricmc.net/v2';

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

module.exports = { ensureFabric, ensureForge189, latestFabricLoader };
