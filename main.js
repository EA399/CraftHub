const { app, BrowserWindow, ipcMain, shell } = require('electron');
const fs = require('fs/promises');
const path = require('path');
const https = require('https');

let win;
const root = () => path.join(app.getPath('userData'), 'instances');
const safeName = s => String(s || '').trim().replace(/[<>:"/\\|?*\x00-\x1F]/g, '-').replace(/[. ]+$/g, '').slice(0, 64);

function requestJson(url, options = {}) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'CraftHub/2.2.0', 'Accept': 'application/json', ...(options.headers || {}) } }, res => {
      let data = '';
      res.setEncoding('utf8');
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        if (res.statusCode < 200 || res.statusCode >= 300) return reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 180)}`));
        try { resolve(JSON.parse(data)); } catch { reject(new Error('Ungültige Serverantwort')); }
      });
    }).on('error', reject);
  });
}

async function listInstances() {
  await fs.mkdir(root(), { recursive: true });
  const entries = await fs.readdir(root(), { withFileTypes: true });
  const result = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const dir = path.join(root(), entry.name);
    let meta = {};
    try { meta = JSON.parse(await fs.readFile(path.join(dir, 'crafthub-instance.json'), 'utf8')); } catch {}
    let mods = [];
    try { mods = (await fs.readdir(path.join(dir, 'mods'))).filter(f => f.toLowerCase().endsWith('.jar')); } catch {}
    result.push({ id: entry.name, name: meta.name || entry.name, version: meta.version || '1.21.1', loader: meta.loader || 'Fabric', mods });
  }
  return result.sort((a,b) => a.name.localeCompare(b.name));
}

async function instanceDir(id) {
  const safe = safeName(id);
  if (!safe || safe !== id) throw new Error('Ungültige Instance');
  const dir = path.join(root(), safe);
  const resolvedRoot = path.resolve(root()) + path.sep;
  if (!path.resolve(dir).startsWith(resolvedRoot)) throw new Error('Ungültiger Pfad');
  return dir;
}

async function createInstance({ name, version, loader }) {
  const displayName = String(name || '').trim();
  const id = safeName(displayName);
  if (!id) throw new Error('Bitte einen gültigen Namen eingeben.');
  if (!/^\d+\.\d+(?:\.\d+)?$/.test(String(version))) throw new Error('Bitte eine gültige Minecraft-Version eingeben, z. B. 1.21.1.');
  if (!['Fabric','Forge','NeoForge','Quilt','Vanilla'].includes(loader)) throw new Error('Ungültiger Loader.');
  const dir = await instanceDir(id);
  try { await fs.access(dir); throw new Error('Eine Instance mit diesem Namen existiert bereits.'); }
  catch (e) { if (e.message.includes('existiert bereits')) throw e; }
  await fs.mkdir(path.join(dir, 'mods'), { recursive: true });
  await fs.writeFile(path.join(dir, 'crafthub-instance.json'), JSON.stringify({ name: displayName, version, loader, createdAt: new Date().toISOString() }, null, 2));
  return listInstances();
}

async function copyInstance({ id, name }) {
  const source = await instanceDir(id);
  const newName = String(name || `${id} Copy`).trim();
  const newId = safeName(newName);
  if (!newId) throw new Error('Bitte einen gültigen Namen eingeben.');
  const target = await instanceDir(newId);
  try { await fs.access(source); } catch { throw new Error('Quell-Instance nicht gefunden.'); }
  try { await fs.access(target); throw new Error('Der Name ist bereits vergeben.'); }
  catch (e) { if (e.message.includes('bereits vergeben')) throw e; }
  await fs.cp(source, target, { recursive: true, errorOnExist: true });
  const metaPath = path.join(target, 'crafthub-instance.json');
  try {
    const meta = JSON.parse(await fs.readFile(metaPath, 'utf8'));
    meta.name = newName;
    meta.copiedFrom = id;
    meta.createdAt = new Date().toISOString();
    await fs.writeFile(metaPath, JSON.stringify(meta, null, 2));
  } catch {}
  return listInstances();
}

async function searchModrinth(query, type = 'mod') {
  const allowed = ['mod', 'modpack', 'resourcepack', 'shader'];
  const projectType = allowed.includes(type) ? type : 'mod';
  const q = encodeURIComponent(String(query || '').trim());
  const url = `https://api.modrinth.com/v2/search?query=${q}&facets=${encodeURIComponent(JSON.stringify([[`project_type:${projectType}`]]))}&limit=24`;
  const data = await requestJson(url);
  return (data.hits || []).map(p => ({
    id: p.project_id, slug: p.slug, title: p.title, description: p.description,
    downloads: p.downloads, icon: p.icon_url, clientSide: p.client_side, serverSide: p.server_side
  }));
}

async function installMod({ instanceId, projectId, version, loader }) {
  const dir = await instanceDir(instanceId);
  let meta;
  try { meta = JSON.parse(await fs.readFile(path.join(dir, 'crafthub-instance.json'), 'utf8')); }
  catch { throw new Error('Instance nicht gefunden.'); }
  const chosenLoader = loader || meta.loader;
  if (chosenLoader === 'Vanilla') throw new Error('Diese Instance hat keinen Mod-Loader. Wähle Fabric, Forge, NeoForge oder Quilt.');
  const url = `https://api.modrinth.com/v2/project/${encodeURIComponent(projectId)}/version?game_versions=${encodeURIComponent(JSON.stringify([meta.version]))}&loaders=${encodeURIComponent(JSON.stringify([chosenLoader.toLowerCase()]))}`;
  const versions = await requestJson(url);
  const compatible = versions.find(v => v.files && v.files.some(f => f.primary)) || versions.find(v => v.files && v.files.length);
  if (!compatible) throw new Error('Keine passende Mod-Version für Minecraft ' + meta.version + ' / ' + chosenLoader + ' gefunden.');
  const file = compatible.files.find(f => f.primary) || compatible.files[0];
  const response = await fetch(file.url);
  if (!response.ok) throw new Error('Download fehlgeschlagen: HTTP ' + response.status);
  const bytes = Buffer.from(await response.arrayBuffer());
  await fs.mkdir(path.join(dir, 'mods'), { recursive: true });
  const destination = path.join(dir, 'mods', path.basename(file.filename).replace(/[<>:"/\\|?*\x00-\x1F]/g, '_'));
  await fs.writeFile(destination, bytes, { flag: 'wx' }).catch(e => { if (e.code === 'EEXIST') throw new Error('Diese Datei ist bereits installiert.'); throw e; });
  return listInstances();
}

function createWindow() {
  win = new BrowserWindow({
    width: 1320, height: 860, minWidth: 980, minHeight: 680,
    backgroundColor: '#101218',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  win.loadFile('index.html');
}
app.whenReady().then(createWindow);
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });

ipcMain.handle('instances:list', () => listInstances());
ipcMain.handle('instances:create', (_, data) => createInstance(data));
ipcMain.handle('instances:copy', (_, data) => copyInstance(data));
ipcMain.handle('instances:delete', async (_, id) => { await fs.rm(await instanceDir(id), { recursive: true, force: true }); return listInstances(); });
ipcMain.handle('instances:open', async (_, id) => { const dir = await instanceDir(id); await shell.openPath(dir); return true; });
ipcMain.handle('mods:search', (_, data) => searchModrinth(data.query, data.type));
ipcMain.handle('mods:install', (_, data) => installMod(data));
ipcMain.handle('auth:modrinth-info', () => ({
  configured: Boolean(process.env.MODRINTH_CLIENT_ID),
  message: 'Für echten Modrinth-OAuth muss eine eigene OAuth-App/Client-ID registriert und der Redirect-Flow ergänzt werden. CraftHub fragt niemals nach deinem Modrinth-Passwort.'
}));
