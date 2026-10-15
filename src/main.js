const { app, BrowserWindow, ipcMain, dialog, shell, nativeTheme, systemPreferences } = require('electron');
const path = require('path');
const paths = require('./core/paths');
const store = require('./core/store');

if (!app.requestSingleInstanceLock()) app.quit();

let win;
const DEFAULT_SETTINGS = { defaultMemoryMb: 4096, javaPath: '', jvmArgs: '', closeOnLaunch: false, width: 0, height: 0 };

const settings = {
  get: () => ({ ...DEFAULT_SETTINGS, ...store.read(paths.settingsFile(), {}) }),
  set: (patch) => {
    const next = { ...settings.get(), ...patch };
    store.write(paths.settingsFile(), next);
    return next;
  },
};

// Window chrome follows Windows' light/dark setting.
const chrome = () =>
  nativeTheme.shouldUseDarkColors
    ? { bg: '#1f1f1f', symbols: '#e6e6e6' }
    : { bg: '#f9f9f9', symbols: '#1a1a1a' };

function accentColor() {
  try {
    const hex = systemPreferences.getAccentColor(); // RRGGBBAA on Windows
    if (hex && /^[0-9a-f]{6}/i.test(hex)) return `#${hex.slice(0, 6)}`;
  } catch { /* not available on this OS */ }
  return null;
}

function createWindow() {
  nativeTheme.themeSource = 'system';
  const c = chrome();
  win = new BrowserWindow({
    width: 1180,
    height: 740,
    minWidth: 900,
    minHeight: 600,
    title: 'SkyLaunch',
    backgroundColor: c.bg,
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#00000000', symbolColor: c.symbols, height: 36 },
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e) => e.preventDefault());
  nativeTheme.on('updated', () => {
    if (!win || win.isDestroyed()) return;
    const n = chrome();
    win.setBackgroundColor(n.bg);
    if (process.platform === 'win32') win.setTitleBarOverlay({ color: '#00000000', symbolColor: n.symbols, height: 36 });
  });
  if (process.platform === 'win32') {
    systemPreferences.on('accent-color-changed', () => {
      if (win && !win.isDestroyed()) win.webContents.send('accent', accentColor());
    });
  }
}

app.on('second-instance', () => {
  if (win) { if (win.isMinimized()) win.restore(); win.focus(); }
});

app.whenReady().then(() => {
  paths.init(app.getPath('userData'));
  // Built client-mod jars ship in resources/client (see client-mod/README.md).
  require('./core/clientmod').init(app.isPackaged ? process.resourcesPath : path.join(__dirname, '..', 'resources'));
  // Loaded after paths.init so they can touch the disk safely.
  const instances = require('./core/instances');
  const auth = require('./core/auth');
  const launcher = require('./core/launch');
  const { PRESETS } = require('./core/presets');
  const modrinth = require('./core/modrinth');

  const send = (channel, payload) => { if (win && !win.isDestroyed()) win.webContents.send(channel, payload); };
  const progress = (label, fraction) => send('progress', { label, fraction });

  // Every handler returns { ok, data } or { ok:false, error } so messages reach the UI intact.
  const handle = (channel, fn) =>
    ipcMain.handle(channel, async (_e, ...args) => {
      try {
        return { ok: true, data: await fn(...args) };
      } catch (err) {
        console.error(`[${channel}]`, err);
        return { ok: false, error: (err && err.message) || String(err) };
      }
    });

  handle('presets:list', () => PRESETS);
  handle('presets:versions', (presetId) => instances.versionChoices(presetId));

  handle('instances:list', () => instances.list());
  handle('instances:create', (opts) => instances.create(opts));
  handle('instances:update', (id, patch) => instances.update(id, patch));
  handle('instances:remove', async (id) => {
    if (launcher.runningId() === id) throw new Error('Close the game before deleting this profile.');
    const { response } = await dialog.showMessageBox(win, {
      type: 'warning',
      buttons: ['Delete profile', 'Keep it'],
      defaultId: 1,
      cancelId: 1,
      message: 'Delete this profile?',
      detail: 'Its mods, settings, screenshots and worlds will be removed from this PC.',
    });
    if (response !== 0) return false;
    instances.remove(id);
    return true;
  });
  handle('instances:installPreset', (id) => instances.installPreset(id, progress));
  handle('instances:changeVersion', (id, v) => instances.changeVersion(id, v, progress));
  handle('instances:openFolder', (id, sub) => shell.openPath(path.join(paths.instanceDir(id), sub || '')));

  handle('mods:list', (id) => instances.listMods(id));
  handle('mods:clientStatus', (id) => instances.clientModStatus(id));
  handle('mods:search', (id, q, opts) => {
    const inst = instances.get(id);
    return modrinth.search(q, inst.loader, inst.mcVersion, opts);
  });
  handle('mods:builds', (id, projectId) => {
    const inst = instances.get(id);
    return modrinth.compatibleBuilds(projectId, inst.loader, inst.mcVersion);
  });
  handle('mods:addModrinth', (id, ref, versionId) => instances.addModrinthMod(id, ref, progress, versionId));
  handle('mods:addFiles', (id, files) => instances.addLocalFiles(id, files));
  handle('mods:pickFiles', async (id) => {
    const { canceled, filePaths } = await dialog.showOpenDialog(win, {
      title: 'Add mod jars',
      filters: [{ name: 'Minecraft mods', extensions: ['jar'] }],
      properties: ['openFile', 'multiSelections'],
    });
    return canceled ? [] : instances.addLocalFiles(id, filePaths);
  });
  handle('mods:setEnabled', (id, file, on) => instances.setModEnabled(id, file, on));
  handle('mods:remove', (id, file) => instances.removeMod(id, file));

  handle('auth:get', () => auth.publicView());
  handle('auth:signIn', () => auth.signIn());
  handle('auth:switch', (uuid) => auth.switchTo(uuid));
  handle('auth:signOut', (uuid) => auth.signOut(uuid));

  handle('settings:get', () => settings.get());
  handle('settings:set', (patch) => settings.set(patch));
  handle('settings:pickJava', async () => {
    const { canceled, filePaths } = await dialog.showOpenDialog(win, {
      title: 'Choose a Java executable',
      filters: process.platform === 'win32' ? [{ name: 'Java', extensions: ['exe'] }] : [],
      properties: ['openFile'],
    });
    return canceled ? null : filePaths[0];
  });

  handle('game:launch', async (id) => {
    const s = settings.get();
    try {
      await launcher.launch(id, {
        settings: s,
        progress,
        log: (line) => send('log', line),
        state: (st, code) => {
          send('game-state', { id, state: st, code });
          if (st === 'running' && s.closeOnLaunch && win) win.minimize();
          if (st === 'stopped' && win && win.isMinimized()) win.restore();
        },
      });
    } catch (e) {
      send('game-state', { id, state: 'stopped' });
      throw e;
    }
    return true;
  });
  handle('game:stop', () => launcher.stop());
  handle('game:running', () => launcher.runningId());
  handle('app:openExternal', (url) => { if (/^https:\/\//.test(url)) shell.openExternal(url); });
  handle('app:version', () => app.getVersion());
  handle('app:accent', () => accentColor());

  createWindow();
});

app.on('window-all-closed', () => app.quit());
