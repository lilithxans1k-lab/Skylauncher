// Offline sanity checks: syntax of every source file, plus version and dependency logic
// against a fake Modrinth. Run with `npm run check`.
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const src = path.join(__dirname, '..', 'src');
const files = [];
(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p);
    else if (p.endsWith('.js')) files.push(p);
  }
})(src);
for (const f of files) execFileSync(process.execPath, ['--check', f]);
console.log(`syntax ok (${files.length} files)`);

const modrinth = require('../src/core/modrinth');
assert(modrinth.compareVersions('26.1', '1.21.11') > 0);
assert(modrinth.compareVersions('1.21.10', '1.21.9') > 0);
assert.deepStrictEqual(modrinth.sortNewestFirst(['1.21.5', '25w14a', '26.1', '1.21.11']), ['26.1', '1.21.11', '1.21.5']);

// Fake Modrinth: two mods plus a shared dependency.
const db = {
  'fabric-api': [{ id: 'fa1', project_id: 'P_FAPI', version_type: 'release', game_versions: ['1.21.10', '1.21.11', '26.1'], version_number: '0.1', dependencies: [], files: [{ primary: true, filename: 'fabric-api.jar', url: 'u', hashes: { sha1: 'x' }, size: 1 }] }],
  'skyblocker-liap': [{ id: 'sb1', project_id: 'P_SB', version_type: 'release', game_versions: ['1.21.10', '1.21.11'], version_number: '5.0', dependencies: [{ project_id: 'P_FAPI', dependency_type: 'required' }, { project_id: 'P_OPT', dependency_type: 'optional' }], files: [{ primary: true, filename: 'skyblocker.jar', url: 'u', hashes: { sha1: 'y' }, size: 1 }] }],
};
db.P_FAPI = db['fabric-api'];
global.fetch = async (url) => {
  const u = decodeURIComponent(url);
  const m = u.match(/\/project\/([^/]+)\/version\?loaders=\["(\w+)"\](?:&game_versions=\["([^"]+)"\])?/);
  let body;
  if (m) {
    const list = db[m[1]];
    if (!list) return { ok: false, status: 404, json: async () => ({}) };
    body = m[3] ? list.filter((v) => v.game_versions.includes(m[3])) : list;
  } else if (u.includes('/projects?ids=')) {
    body = [{ id: 'P_SB', slug: 'skyblocker-liap', title: 'Skyblocker' }, { id: 'P_FAPI', slug: 'fabric-api', title: 'Fabric API' }];
  } else throw new Error('unexpected ' + u);
  return { ok: true, status: 200, json: async () => body };
};

(async () => {
  assert.strictEqual(await modrinth.newestCommonVersion(['fabric-api', 'skyblocker-liap'], 'fabric'), '1.21.11');
  const { mods, missing } = await modrinth.resolveMods({ required: ['skyblocker-liap'], optional: ['nope'] }, 'fabric', '1.21.11');
  assert.deepStrictEqual(missing, []);
  assert.deepStrictEqual(mods.map((m) => m.title).sort(), ['Fabric API', 'Skyblocker']);
  const r2 = await modrinth.resolveMods({ required: ['skyblocker-liap'] }, 'fabric', '26.1');
  assert.deepStrictEqual(r2.missing, ['skyblocker-liap']);
  console.log('modrinth logic ok');
  await shaderTest();
})().catch((e) => { console.error(e); process.exit(1); });

// Shader packs: install from a fake Modrinth, pick one, turn off, remove.
async function shaderTest() {
  const os = require('os');
  const fs2 = require('fs');
  const tmp = fs2.mkdtempSync(path.join(os.tmpdir(), 'skylaunch-check-'));
  const paths = require('../src/core/paths');
  paths.init(tmp);
  const instances = require('../src/core/instances');
  const crypto = require('crypto');
  const zip = Buffer.from('fake shader zip');
  const sha1 = crypto.createHash('sha1').update(zip).digest('hex');
  const shaderBuild = { id: 'SV1', project_id: 'P_SHADER', version_number: 'r5.9', version_type: 'release', game_versions: ['26.2'], dependencies: [], files: [{ primary: true, filename: 'Complementary_r5.9.zip', url: 'https://cdn/shader.zip', hashes: { sha1 }, size: zip.length }] };
  const irisBuild = { id: 'IV1', project_id: 'P_IRIS', version_number: '1.11', version_type: 'release', game_versions: ['26.2'], dependencies: [], files: [{ primary: true, filename: 'iris.jar', url: 'https://cdn/iris.jar', hashes: { sha1 }, size: zip.length }] };
  global.fetch = async (url) => {
    const u = decodeURIComponent(url);
    const json = (b) => ({ ok: true, status: 200, json: async () => b });
    if (u.startsWith('https://cdn/')) return { ok: true, status: 200, headers: { get: () => String(zip.length) }, body: new Blob([zip]).stream() };
    if (u.includes('/project/iris/version')) return json([irisBuild]);
    if (u.includes('/project/complementary/version')) return json(u.includes('game_versions') ? [] : [shaderBuild]);
    if (u.includes('/projects?ids=')) return json([{ id: 'P_IRIS', slug: 'iris', title: 'Iris Shaders' }]);
    throw new Error('unexpected ' + u);
  };
  const inst = await instances.create({ name: 'Test', presetId: 'empty-modern', mcVersion: '26.2' });
  await instances.addShader(inst.id, 'complementary', () => {}, null, { title: 'Complementary' });
  let sh = instances.listShaders(inst.id);
  assert.strictEqual(sh.iris, true, 'Iris added');
  assert.strictEqual(sh.packs.length, 1);
  assert.strictEqual(sh.active, 'Complementary_r5.9.zip', 'first pack is switched on');
  assert.match(fs2.readFileSync(path.join(paths.instanceDir(inst.id), 'config', 'iris.properties'), 'utf8'), /shaderPack=Complementary_r5\.9\.zip/);
  instances.setActiveShader(inst.id, null);
  assert.strictEqual(instances.listShaders(inst.id).enabled, false, 'turned off');
  instances.removeShader(inst.id, 'Complementary_r5.9.zip');
  assert.strictEqual(instances.listShaders(inst.id).packs.length, 0, 'removed');
  fs2.rmSync(tmp, { recursive: true, force: true });
  console.log('shader packs ok');
}
