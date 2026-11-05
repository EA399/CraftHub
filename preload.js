const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("craftHub", {
  listInstances: () => ipcRenderer.invoke("instances:list"),
  createInstance: data => ipcRenderer.invoke("instances:create", data),
  deleteInstance: id => ipcRenderer.invoke("instances:delete", id),
  openInstanceFolder: id => ipcRenderer.invoke("instances:openFolder", id),
  launchInstance: id => ipcRenderer.invoke("instances:launch", id),
  searchModrinth: filters => ipcRenderer.invoke("modrinth:search", filters),
  installMod: data => ipcRenderer.invoke("modrinth:install", data),
  chooseMinecraft: () => ipcRenderer.invoke("system:chooseMinecraft"),
  openExternal: url => ipcRenderer.invoke("system:openExternal", url)
});
