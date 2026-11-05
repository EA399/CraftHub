const { app, BrowserWindow, ipcMain, shell, dialog } = require("electron");
const path = require("node:path");
const fs = require("node:fs/promises");
const https = require("node:https");
const { spawn } = require("node:child_process");

const root = () => path.join(app.getPath("userData"), "instances");
const safeId = (id) => String(id || "").replace(/[^a-zA-Z0-9_-]/g, "");
const jsonRead = async (file, fallback) => { try { return JSON.parse(await fs.readFile(file, "utf8")); } catch { return fallback; } };
async function ensure() { await fs.mkdir(root(), { recursive: true }); }
async function getInstances() {
  await ensure();
  const dirs = await fs.readdir(root(), { withFileTypes: true });
  const out = [];
  for (const d of dirs) if (d.isDirectory()) {
    const folder = path.join(root(), d.name);
    const meta = await jsonRead(path.join(folder, "instance.json"), null);
    if (meta) {
      const modsDir = path.join(folder, "mods");
      await fs.mkdir(modsDir, { recursive: true });
      const mods = (await fs.readdir(modsDir)).filter(f => f.toLowerCase().endsWith(".jar"));
      out.push({ ...meta, path: folder, mods });
    }
  }
  return out.sort((a,b) => a.name.localeCompare(b.name));
}
function request(url, opts = {}) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { "User-Agent": "CraftHub/2.1.0 (desktop launcher)", ...(opts.headers || {}) } }, res => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume(); return resolve(request(res.headers.location, opts));
      }
      if (res.statusCode < 200 || res.statusCode >= 300) { res.resume(); return reject(new Error(`HTTP ${res.statusCode}`)); }
      if (opts.stream) return resolve(res);
      let body = ""; res.setEncoding("utf8"); res.on("data", x => body += x); res.on("end", () => { try { resolve(JSON.parse(body)); } catch { reject(new Error("Ungültige Antwort vom Server")); } });
    }).on("error", reject);
  });
}
ipcMain.handle("instances:list", getInstances);
ipcMain.handle("instances:create", async (_e, data) => {
  const name = String(data?.name || "").trim().slice(0, 48);
  if (!name) throw new Error("Bitte einen Namen eingeben.");
  await ensure();
  const id = `${Date.now()}-${Math.random().toString(36).slice(2,8)}`;
  const folder = path.join(root(), id);
  await fs.mkdir(path.join(folder, "mods"), { recursive: true });
  await fs.mkdir(path.join(folder, "resourcepacks"), { recursive: true });
  await fs.mkdir(path.join(folder, "shaderpacks"), { recursive: true });
  await fs.mkdir(path.join(folder, "saves"), { recursive: true });
  const meta = { id, name, minecraftVersion: data.minecraftVersion || "1.21.1", loader: data.loader || "fabric", createdAt: new Date().toISOString(), notes: "Instance created by CraftHub" };
  await fs.writeFile(path.join(folder, "instance.json"), JSON.stringify(meta, null, 2));
  return (await getInstances()).find(x => x.id === id);
});
ipcMain.handle("instances:delete", async (_e, id) => {
  const folder = path.join(root(), safeId(id));
  if (!path.resolve(folder).startsWith(path.resolve(root()) + path.sep)) throw new Error("Ungültige Instance.");
  await fs.rm(folder, { recursive: true, force: true });
  return getInstances();
});
ipcMain.handle("instances:openFolder", async (_e, id) => {
  const instance = (await getInstances()).find(x => x.id === safeId(id));
  if (!instance) throw new Error("Instance nicht gefunden.");
  await shell.openPath(instance.path);
});
ipcMain.handle("instances:launch", async (_e, id) => {
  const instance = (await getInstances()).find(x => x.id === safeId(id));
  if (!instance) throw new Error("Instance nicht gefunden.");
  // The app deliberately does not bypass Microsoft/Xbox authentication. Full launch requires
  // a registered OAuth client and the official Minecraft authentication flow.
  throw new Error("Der direkte Spielstart benötigt noch die offizielle Microsoft-/Xbox-/Minecraft-Anmeldung. Deine Instance-Dateien sind bereits vorbereitet; bitte nutze bis dahin den offiziellen Launcher.");
});
ipcMain.handle("modrinth:search", async (_e, { query = "", version = "", loader = "fabric", type = "mod" } = {}) => {
  const facets = [[`project_type:${type}`]];
  if (version) facets.push([`versions:${version}`]);
  if (loader && loader !== "vanilla" && type === "mod") facets.push([`categories:${loader}`]);
  const url = new URL("https://api.modrinth.com/v2/search");
  url.searchParams.set("query", query);
  url.searchParams.set("limit", "30");
  url.searchParams.set("index", "relevance");
  url.searchParams.set("facets", JSON.stringify(facets));
  const data = await request(url.toString());
  return (data.hits || []).map(p => ({
    id: p.project_id, slug: p.slug, title: p.title, author: p.author, description: p.description,
    icon: p.icon_url, downloads: p.downloads, categories: p.display_categories || p.categories || [], projectType: p.project_type
  }));
});
ipcMain.handle("modrinth:versions", async (_e, { projectId, version, loader }) => {
  const url = new URL(`https://api.modrinth.com/v2/project/${encodeURIComponent(projectId)}/version`);
  if (version) url.searchParams.set("game_versions", JSON.stringify([version]));
  if (loader && loader !== "vanilla") url.searchParams.set("loaders", JSON.stringify([loader]));
  const data = await request(url.toString());
  return (data || []).map(v => ({ id:v.id, name:v.name, versionNumber:v.version_number, gameVersions:v.game_versions, loaders:v.loaders, files:v.files, dependencies:v.dependencies }));
});
ipcMain.handle("modrinth:install", async (_e, { instanceId, projectId }) => {
  const instance = (await getInstances()).find(x => x.id === safeId(instanceId));
  if (!instance) throw new Error("Instance nicht gefunden.");
  const url = new URL(`https://api.modrinth.com/v2/project/${encodeURIComponent(projectId)}/version`);
  url.searchParams.set("game_versions", JSON.stringify([instance.minecraftVersion]));
  if (instance.loader !== "vanilla") url.searchParams.set("loaders", JSON.stringify([instance.loader]));
  const versions = await request(url.toString());
  const version = versions.find(v => v.files?.some(f => f.primary)) || versions.find(v => v.files?.length);
  if (!version) throw new Error("Keine passende Datei für diese Minecraft-Version und diesen Loader gefunden.");
  const file = version.files.find(f => f.primary) || version.files[0];
  if (!file.url || !file.filename.toLowerCase().endsWith(".jar")) throw new Error("Modrinth hat keine passende JAR-Datei geliefert.");
  const destination = path.join(instance.path, "mods", path.basename(file.filename));
  try { await fs.access(destination); throw new Error("Diese Datei ist bereits installiert."); } catch (e) { if (e.message === "Diese Datei ist bereits installiert.") throw e; }
  const res = await request(file.url, { stream: true });
  await new Promise((resolve, reject) => {
    const out = require("node:fs").createWriteStream(destination);
    res.pipe(out); out.on("finish", resolve); out.on("error", reject);
  });
  return { filename: file.filename, instance: instance.name };
});
ipcMain.handle("system:chooseMinecraft", async () => {
  const result = await dialog.showOpenDialog({ properties: ["openDirectory"], title: "Minecraft-Ordner auswählen" });
  return result.canceled ? null : result.filePaths[0];
});
ipcMain.handle("system:openExternal", async (_e, url) => {
  if (typeof url === "string" && /^https:\/\/(modrinth\.com|github\.com|learn\.microsoft\.com)/i.test(url)) await shell.openExternal(url);
});
function createWindow() {
  const win = new BrowserWindow({ width: 1480, height: 940, minWidth: 1000, minHeight: 700, title: "CraftHub", backgroundColor: "#0b0d12", autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, nodeIntegration: false, sandbox: true } });
  win.removeMenu();
  win.loadFile(path.join(__dirname, "index.html"));
}
app.whenReady().then(createWindow);
app.on("window-all-closed", () => { if (process.platform !== "darwin") app.quit(); });
