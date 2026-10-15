// Launcher updates from GitHub Releases (lilithxans1k-lab/Skylauncher).
// On start the launcher checks for a newer release and downloads it in the background.
// The update installs when you click "Restart to update", or by itself the next time
// you close SkyLaunch. It never restarts while Minecraft is running.
const { app } = require('electron');

let autoUpdater = null;
let state = { status: 'idle', version: null, progress: 0, error: null, current: app.getVersion() };
let notify = () => {};
let isGameRunning = () => false;

function set(patch) {
  state = { ...state, ...patch };
  notify(state);
}

function friendly(err) {
  const msg = String((err && err.message) || err || '');
  if (/404|Not Found|No published versions/i.test(msg)) return 'No releases have been published yet.';
  if (/ENOTFOUND|ETIMEDOUT|ECONNRESET|EAI_AGAIN|net::/i.test(msg)) return "Couldn't reach GitHub. Check your internet connection.";
  return msg.split('\n')[0].slice(0, 200) || 'Update check failed.';
}

function init({ onState, gameRunning }) {
  notify = onState || notify;
  isGameRunning = gameRunning || isGameRunning;
  if (!app.isPackaged) {
    set({ status: 'dev' });
    return;
  }
  ({ autoUpdater } = require('electron-updater'));
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.allowPrerelease = false;
  autoUpdater.logger = null;

  autoUpdater.on('checking-for-update', () => set({ status: 'checking', error: null }));
  autoUpdater.on('update-available', (info) => set({ status: 'downloading', version: info.version, progress: 0 }));
  autoUpdater.on('update-not-available', () => set({ status: 'latest', version: null }));
  autoUpdater.on('download-progress', (p) => set({ status: 'downloading', progress: (p.percent || 0) / 100 }));
  autoUpdater.on('update-downloaded', (info) => set({ status: 'ready', version: info.version, progress: 1 }));
  autoUpdater.on('error', (err) => set({ status: 'error', error: friendly(err) }));

  check();
  // Long sessions: look again every 4 hours.
  setInterval(check, 4 * 60 * 60 * 1000).unref();
}

async function check() {
  if (!autoUpdater) return state;
  if (state.status === 'downloading' || state.status === 'ready') return state;
  try {
    await autoUpdater.checkForUpdates();
  } catch (e) {
    set({ status: 'error', error: friendly(e) });
  }
  return state;
}

function installNow() {
  if (!autoUpdater || state.status !== 'ready') throw new Error('No update is ready yet.');
  if (isGameRunning()) throw new Error('Close Minecraft first, then restart to update.');
  // Silent install, then reopen SkyLaunch.
  setImmediate(() => autoUpdater.quitAndInstall(true, true));
  return true;
}

module.exports = { init, check, installNow, get: () => state };
