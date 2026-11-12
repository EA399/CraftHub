const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('crafthub', {
  versions: () => ipcRenderer.invoke('versions'),
  login: () => ipcRenderer.invoke('login'),
  play: (opts) => ipcRenderer.invoke('play', opts),
  on: (channel, cb) => ipcRenderer.on(channel, (_e, data) => cb(data))
});
