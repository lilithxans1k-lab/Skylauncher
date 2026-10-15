// Profiles ("instances"): each has its own game folder, mods and settings.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const paths = require('./paths');
const store = require('./store');
const modrinth = require('./modrinth');
const { download } = require('./net');
const { getPreset, FORGE_189 } = require('./presets');
const clientMod = require('./clientmod');

const metaFile = (id) => path.join(paths.instanceDir(id), 'instance.json');

function list() {
  if (!fs.existsSync(paths.instancesDir())) return [];
  return fs
    .readdirSync(paths.instancesDir())
    .map((id) => store.read(metaFile(id), null))
    .filter(Boolean)
    .sort((a, b) => (b.lastPlayed || b.created) - (a.lastPlayed || a.created));
}

function get(id) {
  const inst = store.read(metaFile(id), null);
  if (!inst) throw new Error('That profile no longer exists.');
  return inst;
}

function save(inst) {
  fs.mkdirSync(paths.instanceDir(inst.id), { recursive: true });
  store.write(metaFile(inst.id), inst);
  return inst;
}

function update(id, patch) {
  const allowed = ['name', 'memoryMb', 'autoJoin', 'jvmArgs', 'javaPath', 'lastPlayed', 'width', 'height', 'clientMod'];
  const inst = get(id);
  for (const k of allowed) if (k in patch) inst[k] = patch[k];
  save(inst);
  if ('clientMod' in patch) clientMod.sync(inst);
  return inst;
}

function makeId(name) {
  const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 24) || 'profile';
  return `${slug}-${crypto.randomBytes(3).toString('hex')}`;
}

/** Version choices for a preset (modern presets only; legacy is always 1.8.9). */
async function versionChoices(presetId) {
  const preset = getPreset(presetId);
  if (!preset) throw new Error('Unknown preset.');
  if (preset.era === 'legacy') return [FORGE_189.mcVersion];
  return modrinth.versionsForPicker(preset.required, preset.loader);
}

/**
 * Create a profile from a preset. For modern presets the Minecraft version defaults to the
 * newest one every required mod supports.
 */
async function create({ name, presetId, mcVersion }) {
  const preset = getPreset(presetId);
  if (!preset) throw new Error('Unknown preset.');

  let version = preset.mcVersion || mcVersion;
  if (!version) {
    // Prefer the version the SkyLaunch in-game client is built for, when the mods support it.
    const choices = await modrinth.versionsForPicker(preset.required, preset.loader);
    version = choices.includes(clientMod.PREFERRED_MODERN) ? clientMod.PREFERRED_MODERN : choices[0] || null;
    if (!version) throw new Error(`The ${preset.name} mods don't share a Minecraft version right now.`);
  }

  const inst = save({
    id: makeId(name || preset.name),
    name: (name || preset.name).trim(),
    presetId,
    loader: preset.loader,
    mcVersion: version,
    loaderVersion: null,
    memoryMb: preset.era === 'legacy' ? 3072 : 4096,
    autoJoin: true,
    clientMod: true,
    jvmArgs: '',
    javaPath: '',
    mods: [],
    created: Date.now(),
    lastPlayed: null,
  });
  fs.mkdirSync(paths.modsDir(inst.id), { recursive: true });
  clientMod.sync(inst);
  return inst;
}

function remove(id) {
  fs.rmSync(paths.instanceDir(id), { recursive: true, force: true });
}

// --- Mods -------------------------------------------------------------------

function fileState(id, file) {
  const dir = paths.modsDir(id);
  if (fs.existsSync(path.join(dir, file))) return 'enabled';
  if (fs.existsSync(path.join(dir, `${file}.disabled`))) return 'disabled';
  return 'missing';
}

/** Everything in the mods folder, tagged with where it came from. */
function listMods(id) {
  const inst = get(id);
  const dir = paths.modsDir(id);
  fs.mkdirSync(dir, { recursive: true });
  const known = new Map(inst.mods.map((m) => [m.file, m]));
  const out = [];
  for (const name of fs.readdirSync(dir)) {
    if (!/\.jar(\.disabled)?$/i.test(name)) continue;
    const file = name.replace(/\.disabled$/i, '');
    if (file === clientMod.FILE) continue; // listed separately as the built-in client
    const meta = known.get(file);
    out.push({
      file,
      enabled: !name.endsWith('.disabled'),
      source: meta ? meta.source : 'local',
      title: meta ? meta.title : file.replace(/\.jar$/i, ''),
      version: meta ? meta.versionNumber : null,
      projectId: meta ? meta.projectId : null,
      icon: meta ? meta.icon : null,
    });
  }
  const order = { preset: 0, modrinth: 1, local: 2 };
  return out.sort((a, b) => order[a.source] - order[b.source] || a.title.localeCompare(b.title));
}

async function downloadMods(id, mods, source, onProgress) {
  const dir = paths.modsDir(id);
  let done = 0;
  for (const m of mods) {
    onProgress(`Downloading ${m.title}`, done / mods.length);
    await download(m.url, path.join(dir, m.filename), { sha1: m.sha1 });
    done++;
  }
  return mods.map((m) => ({
    file: m.filename, projectId: m.projectId, versionId: m.versionId, title: m.title,
    versionNumber: m.versionNumber, icon: m.icon || null, source,
  }));
}

/**
 * Install (or refresh) the preset's mods. Removes the previous preset files first, keeps
 * anything the player added. Returns { installed, missing }.
 */
async function installPreset(id, onProgress = () => {}) {
  const inst = get(id);
  const preset = getPreset(inst.presetId);
  if (!preset) return { installed: 0, missing: [] };

  onProgress('Finding mods on Modrinth', 0);
  const { mods, missing } = await modrinth.resolveMods(preset, inst.loader, inst.mcVersion);
  if (missing.length) {
    throw new Error(`No ${inst.mcVersion} build exists for: ${missing.join(', ')}. Pick a different version for this profile.`);
  }

  // Remember disabled preset mods by project so a refresh keeps them disabled.
  const disabledProjects = new Set(
    inst.mods.filter((m) => m.source === 'preset' && fileState(id, m.file) === 'disabled').map((m) => m.projectId),
  );
  // Mods the player added themselves win over the preset's copy.
  const userProjects = new Set(inst.mods.filter((m) => m.source !== 'preset').map((m) => m.projectId));
  const wanted = mods.filter((m) => !userProjects.has(m.projectId));

  for (const old of inst.mods.filter((m) => m.source === 'preset')) {
    for (const f of [old.file, `${old.file}.disabled`]) fs.rmSync(path.join(paths.modsDir(id), f), { force: true });
  }

  const records = await downloadMods(id, wanted, 'preset', onProgress);
  for (const r of records) {
    if (disabledProjects.has(r.projectId)) {
      fs.renameSync(path.join(paths.modsDir(id), r.file), path.join(paths.modsDir(id), `${r.file}.disabled`));
    }
  }

  const fresh = get(id);
  fresh.mods = [...fresh.mods.filter((m) => m.source !== 'preset'), ...records];
  fresh.presetInstalledAt = Date.now();
  clientMod.sync(fresh);
  save(fresh);
  onProgress('Mods ready', 1);
  return { installed: records.length, missing };
}

/**
 * Add a mod from Modrinth (plus anything it needs that isn't already installed).
 * versionId picks an exact build; otherwise the newest stable one for this profile is used.
 */
async function addModrinthMod(id, ref, onProgress = () => {}, versionId = null) {
  const inst = get(id);
  const required = versionId ? [{ versionId }] : [ref];
  const { mods, missing } = await modrinth.resolveMods({ required }, inst.loader, inst.mcVersion);
  if (missing.length) throw new Error(`No ${inst.mcVersion} ${inst.loader} build of that mod exists.`);
  const have = new Map(inst.mods.map((m) => [m.projectId, m]));
  // Picking a different build of an installed mod replaces it.
  const main = mods[0];
  if (versionId && main && have.has(main.projectId)) {
    removeMod(id, have.get(main.projectId).file);
    have.delete(main.projectId);
  }
  const toAdd = mods.filter((m) => !have.has(m.projectId));
  const records = await downloadMods(id, toAdd, 'modrinth', onProgress);
  const fresh = get(id);
  const onDisk = new Set(fs.readdirSync(paths.modsDir(id)));
  // A file the player already dropped in by hand becomes a tracked Modrinth mod.
  fresh.mods = fresh.mods.filter((m) => onDisk.has(m.file) || onDisk.has(`${m.file}.disabled`));
  fresh.mods.push(...records);
  save(fresh);
  onProgress('Mod added', 1);
  return records;
}

/** Copy jars the player picked or dropped onto the window. */
function addLocalFiles(id, files) {
  const dir = paths.modsDir(id);
  const added = [];
  for (const f of files) {
    if (!/\.jar$/i.test(f)) continue;
    fs.copyFileSync(f, path.join(dir, path.basename(f)));
    added.push(path.basename(f));
  }
  return added;
}

function setModEnabled(id, file, enabled) {
  const dir = paths.modsDir(id);
  const on = path.join(dir, file);
  const off = `${on}.disabled`;
  if (enabled && fs.existsSync(off)) fs.renameSync(off, on);
  if (!enabled && fs.existsSync(on)) fs.renameSync(on, off);
}

function removeMod(id, file) {
  const dir = paths.modsDir(id);
  for (const f of [file, `${file}.disabled`]) fs.rmSync(path.join(dir, f), { force: true });
  const inst = get(id);
  inst.mods = inst.mods.filter((m) => m.file !== file);
  save(inst);
}

/** Switch a modern profile to another Minecraft version and reinstall its mods. */
async function changeVersion(id, mcVersion, onProgress) {
  const inst = get(id);
  if (inst.loader !== 'fabric') throw new Error('1.8.9 profiles stay on 1.8.9.');
  const preset = getPreset(inst.presetId);
  // Check the preset works on the new version before touching any files.
  if (preset) {
    onProgress('Checking mods for ' + mcVersion, 0);
    const { missing } = await modrinth.resolveMods({ required: preset.required }, inst.loader, mcVersion);
    if (missing.length) throw new Error(`No ${mcVersion} build exists for: ${missing.join(', ')}.`);
  }
  // Mods the player added from Modrinth are version-specific, so swap them for matching builds.
  const userModrinth = inst.mods.filter((m) => m.source === 'modrinth');
  for (const m of userModrinth) removeMod(id, m.file);

  const fresh = get(id);
  fresh.mcVersion = mcVersion;
  fresh.loaderVersion = null;
  save(fresh);
  await installPreset(id, onProgress);

  const skipped = [];
  for (const m of userModrinth) {
    try {
      await addModrinthMod(id, m.projectId, onProgress);
    } catch {
      skipped.push(m.title);
    }
  }
  clientMod.sync(get(id));
  return { skipped };
}

/** Built-in SkyLaunch client state for the Mods tab. */
function clientModStatus(id) {
  return clientMod.status(get(id));
}

module.exports = {
  clientModStatus,
  list, get, create, update, remove, versionChoices, listMods, installPreset,
  addModrinthMod, addLocalFiles, setModEnabled, removeMod, changeVersion,
};
