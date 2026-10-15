// The only bridge between the UI and the launcher's Node side.
const { contextBridge, ipcRenderer, webUtils } = require('electron');

const call = (channel) => async (...args) => {
  const res = await ipcRenderer.invoke(channel, ...args);
  if (!res.ok) throw new Error(res.error);
  return res.data;
};

const on = (channel) => (fn) => {
  const listener = (_e, payload) => fn(payload);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
};

contextBridge.exposeInMainWorld('sky', {
  presets: { list: call('presets:list'), versions: call('presets:versions') },
  instances: {
    list: call('instances:list'),
    create: call('instances:create'),
    update: call('instances:update'),
    remove: call('instances:remove'),
    installPreset: call('instances:installPreset'),
    changeVersion: call('instances:changeVersion'),
    openFolder: call('instances:openFolder'),
  },
  mods: {
    list: call('mods:list'),
    clientStatus: call('mods:clientStatus'),
    search: call('mods:search'),
    builds: call('mods:builds'),
    addModrinth: call('mods:addModrinth'),
    addFiles: call('mods:addFiles'),
    pickFiles: call('mods:pickFiles'),
    setEnabled: call('mods:setEnabled'),
    remove: call('mods:remove'),
  },
  auth: {
    get: call('auth:get'),
    signIn: call('auth:signIn'),
    switchTo: call('auth:switch'),
    signOut: call('auth:signOut'),
  },
  update: { get: call('update:get'), check: call('update:check'), install: call('update:install') },
  settings: { get: call('settings:get'), set: call('settings:set'), pickJava: call('settings:pickJava') },
  game: { launch: call('game:launch'), stop: call('game:stop'), running: call('game:running') },
  app: { openExternal: call('app:openExternal'), version: call('app:version'), accent: call('app:accent') },
  // Dropped files carry no path in the sandbox; this recovers it.
  pathForFile: (file) => webUtils.getPathForFile(file),
  onProgress: on('progress'),
  onLog: on('log'),
  onGameState: on('game-state'),
  onAccent: on('accent'),
  onUpdate: on('update-state'),
});
