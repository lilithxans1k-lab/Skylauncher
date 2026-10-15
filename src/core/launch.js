// Prepares everything a profile needs and starts the game.
const { Client } = require('minecraft-launcher-core');
const paths = require('./paths');
const instances = require('./instances');
const loaders = require('./loaders');
const java = require('./java');
const auth = require('./auth');
const modrinth = require('./modrinth');
const { HYPIXEL_HOST } = require('./presets');
const clientMod = require('./clientmod');
const share = require('./share');
const { execFile } = require('child_process');
const Handler = require('minecraft-launcher-core/components/handler');

// The launch library's Java check throws (and would crash the launcher) when `-version`
// output doesn't match its pattern, which can happen with javaw.exe. We launch with javaw.exe
// so Windows doesn't open a console window next to the game, so swap in a tolerant check.
Handler.prototype.checkJava = function checkJava(javaBin) {
  return new Promise((resolve) => {
    execFile(javaBin, ['-version'], { windowsHide: true }, (error, stdout, stderr) => {
      if (error) return resolve({ run: false, message: error });
      const m = `${stderr}${stdout}`.match(/"(.*?)"/);
      this.client.emit('debug', `[MCLC]: Using Java ${m ? m[1] : '(version not reported)'}`);
      resolve({ run: true });
    });
  });
};

let running = null; // { id, child }

function splitArgs(str) {
  return (str || '').match(/(?:[^\s"]+|"[^"]*")+/g)?.map((a) => a.replace(/^"|"$/g, '')) || [];
}

/**
 * hooks: { progress(label, fraction), log(line), state(state), settings }
 * state values: 'preparing' | 'running' | 'stopped'
 */
async function launch(id, hooks) {
  if (running) throw new Error('A game is already running. Close it first.');
  const { progress, log, state, settings } = hooks;
  const inst = instances.get(id);
  state('preparing');

  // Install preset mods the first time a profile is played.
  if (!inst.presetInstalledAt) await instances.installPreset(id, progress);
  await ensureSharingMod(id, progress, log);
  clientMod.sync(instances.get(id));
  try { share.writeManifest(id); } catch (e) { log(`[SkyLaunch] Couldn't write the mod list for invites: ${e.message}`); }

  progress('Signing in', 0);
  const authorization = await auth.launchAuth();

  progress('Setting up the mod loader', 0);
  const version = { number: inst.mcVersion, type: 'release' };
  let forge;
  if (inst.loader === 'fabric') {
    const { customId, loaderVersion } = await loaders.ensureFabric(inst.mcVersion, inst.loaderVersion);
    version.custom = customId;
    log(`[SkyLaunch] Fabric Loader ${loaderVersion}`);
  } else {
    ({ forgeJar: forge } = await loaders.ensureForge189());
  }

  let javaPath = inst.javaPath || settings.javaPath;
  if (!javaPath) {
    const major = await java.requiredJavaMajor(inst.mcVersion);
    javaPath = await java.ensureJava(major, progress);
    log(`[SkyLaunch] Using Java ${major}: ${javaPath}`);
  }

  const memoryMb = inst.memoryMb || settings.defaultMemoryMb || 4096;
  const modern = modrinth.compareVersions(inst.mcVersion, '1.20') >= 0;
  const opts = {
    authorization,
    root: paths.root(),
    version,
    forge,
    javaPath,
    memory: { max: `${memoryMb}M`, min: `${Math.min(1024, memoryMb)}M` },
    customArgs: splitArgs(inst.jvmArgs || settings.jvmArgs),
    overrides: { gameDirectory: paths.instanceDir(id), detached: false },
    window: settings.width && settings.height ? { width: String(settings.width), height: String(settings.height) } : undefined,
  };
  if (hooks.joinAddress) {
    opts.quickPlay = { type: 'multiplayer', identifier: hooks.joinAddress };
    log(`[SkyLaunch] Joining ${hooks.joinAddress}`);
  } else if (inst.autoJoin) {
    opts.quickPlay = modern
      ? { type: 'multiplayer', identifier: HYPIXEL_HOST }
      : { type: 'legacy', identifier: `${HYPIXEL_HOST}:25565` };
  }

  const client = new Client();
  client.on('progress', (e) => progress(`Downloading ${e.type}`, e.total ? e.task / e.total : 0));
  client.on('debug', (e) => log(String(e)));
  client.on('data', (e) => String(e).split(/\r?\n/).filter(Boolean).forEach(log));

  progress('Starting Minecraft', 1);
  const child = await client.launch(opts);
  if (!child) {
    state('stopped');
    throw new Error('Minecraft failed to start. Open the Logs tab for details.');
  }
  running = { id, child };
  instances.update(id, { lastPlayed: Date.now() });
  state('running');

  client.on('close', (code) => {
    running = null;
    log(`[SkyLaunch] Minecraft exited with code ${code}`);
    state('stopped', code);
  });
  return true;
}

/**
 * Modern profiles with the SkyLaunch Client get e4mc, which powers Share world.
 * If Modrinth is unreachable the game still starts; sharing just says the mod is missing.
 */
async function ensureSharingMod(id, progress, log) {
  const inst = instances.get(id);
  if (inst.loader !== 'fabric' || inst.clientMod === false) return;
  if (inst.mods.some((m) => /e4mc/i.test(`${m.title} ${m.file}`))) return;
  try {
    progress('Adding world sharing (e4mc)', 0);
    await instances.addModrinthMod(id, 'e4mc', progress);
  } catch (e) {
    log(`[SkyLaunch] Couldn't add e4mc for world sharing: ${e.message}`);
  }
}

function stop() {
  if (running && running.child) running.child.kill();
}

const runningId = () => (running ? running.id : null);

module.exports = { launch, stop, runningId };
