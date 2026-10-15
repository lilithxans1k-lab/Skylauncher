// Shared worlds.
// Host side: at launch, the launcher writes skylaunch-share.json (the profile's exact mods) into
// the game folder. The in-game Share world panel turns that plus the e4mc address into an invite.
// Friend side: "Join a friend" decodes the invite, shows which mods are needed, installs them into
// a profile for that host, and launches straight into the world.
// The invite format matches client-mod/core/.../share/Invite.java.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const paths = require('./paths');
const instances = require('./instances');

const PREFIX = 'skylaunch:';
const MANIFEST = 'skylaunch-share.json';

/** Write the profile's mod list for the in-game invite builder. */
function writeManifest(id) {
  const inst = instances.get(id);
  const enabled = new Set(instances.listMods(id).filter((m) => m.enabled).map((m) => m.file));
  const mods = [];
  const files = [];
  for (const m of inst.mods) {
    if (!enabled.has(m.file)) continue;
    if (m.versionId) mods.push({ v: m.versionId, p: m.projectId, t: m.title });
  }
  for (const m of instances.listMods(id)) {
    if (m.enabled && m.source === 'local') files.push(m.file);
  }
  const manifest = { mc: inst.mcVersion, loader: inst.loader, mods, files };
  fs.writeFileSync(path.join(paths.instanceDir(id), MANIFEST), JSON.stringify(manifest));
}

/** Invite code -> plain object. Throws a readable error for anything that isn't an invite. */
function decode(code) {
  const text = String(code || '').trim();
  const at = text.indexOf(PREFIX);
  if (at < 0) {
    if (/\.e4mc\.link/i.test(text)) {
      throw new Error("That's an address, not an invite. Ask your friend for the invite (Copy invite in their Share world panel), or add the address as a server in game.");
    }
    throw new Error("That doesn't look like a SkyLaunch invite. It should start with skylaunch:");
  }
  const body = text.slice(at + PREFIX.length).split(/\s/)[0];
  let json;
  try {
    json = JSON.parse(zlib.inflateRawSync(Buffer.from(body, 'base64url')).toString('utf8'));
  } catch {
    throw new Error('That invite is incomplete or damaged. Ask your friend to copy it again.');
  }
  if (!json.a) throw new Error('That invite has no address in it.');
  return {
    address: String(json.a),
    host: String(json.h || 'Friend'),
    mcVersion: String(json.mc || ''),
    loader: String(json.l || 'fabric'),
    mods: Array.isArray(json.m) ? json.m.filter((m) => m && m.v).map((m) => ({ versionId: String(m.v), projectId: m.p || null, title: m.t || m.v })) : [],
    files: Array.isArray(json.f) ? json.f.map(String) : [],
  };
}

function profileFor(invite) {
  return instances.list().find((i) => i.sharedFrom === invite.host && i.mcVersion === invite.mcVersion && i.loader === invite.loader) || null;
}

/** What joining would do, for the pop-up. */
function plan(code) {
  const invite = decode(code);
  if (invite.loader !== 'fabric') throw new Error(`This invite is for ${invite.loader}, which SkyLaunch can't join shared worlds on.`);
  const existing = profileFor(invite);
  const have = new Set(existing ? existing.mods.map((m) => m.versionId).filter(Boolean) : []);
  return {
    ...invite,
    profileName: existing ? existing.name : `${invite.host}'s world`,
    profileExists: !!existing,
    mods: invite.mods.map((m) => ({ ...m, installed: have.has(m.versionId) })),
  };
}

/**
 * Make (or update) the friend's profile so its Modrinth mods match the host exactly, then return
 * { id, address } for the launcher to start.
 */
async function prepare(code, onProgress = () => {}) {
  const invite = decode(code);
  let inst = profileFor(invite);
  if (!inst) {
    inst = await instances.create({ name: `${invite.host}'s world`, presetId: 'empty-modern', mcVersion: invite.mcVersion });
    instances.markShared(inst.id, invite.host);
  }
  const id = inst.id;
  const wanted = new Set(invite.mods.map((m) => m.versionId));

  // Remove Modrinth mods the host doesn't have (their own dropped-in jars are left alone).
  for (const m of instances.get(id).mods) {
    if ((m.source === 'preset' || m.source === 'modrinth') && m.versionId && !wanted.has(m.versionId)) {
      instances.removeMod(id, m.file);
    }
  }
  // Install the host's exact versions. Required libraries come along automatically.
  const failed = [];
  let done = 0;
  for (const m of invite.mods) {
    const have = instances.get(id).mods.some((x) => x.versionId === m.versionId);
    onProgress(`Installing ${m.title}`, done / Math.max(1, invite.mods.length));
    if (!have) {
      try {
        await instances.addModrinthMod(id, m.projectId || m.versionId, () => {}, m.versionId);
      } catch {
        failed.push(m.title);
      }
    }
    done++;
  }
  onProgress('Mods ready', 1);
  return { id, address: invite.address, failed, files: invite.files };
}

module.exports = { writeManifest, decode, plan, prepare, MANIFEST };
