const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('craft', {
  listInstances: () => ipcRenderer.invoke('instances:list'),
  createInstance: data => ipcRenderer.invoke('instances:create', data),
  copyInstance: data => ipcRenderer.invoke('instances:copy', data),
  deleteInstance: id => ipcRenderer.invoke('instances:delete', id),
  openInstance: id => ipcRenderer.invoke('instances:open', id),
  searchMods: data => ipcRenderer.invoke('mods:search', data),
  installMod: data => ipcRenderer.invoke('mods:install', data),
  modrinthInfo: () => ipcRenderer.invoke('auth:modrinth-info')
});
