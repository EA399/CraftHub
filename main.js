const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const { Client } = require('minecraft-launcher-core');
const { Auth } = require('msmc');

let win;
let session = null; // Microsoft/Minecraft-Login (nur im Speicher)

function createWindow() {
  win = new BrowserWindow({
    width: 900,
    height: 600,
    backgroundColor: '#1b2a2e',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  win.loadFile(path.join(__dirname, 'index.html'));
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());

const send = (channel, data) => win && !win.isDestroyed() && win.webContents.send(channel, data);

// Minecraft-Versionen von Mojang laden (nur Releases)
ipcMain.handle('versions', async () => {
  const res = await fetch('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json');
  const data = await res.json();
  return data.versions.filter(v => v.type === 'release').map(v => v.id);
});

// Login mit Microsoft-Konto (Besitz von Minecraft Java wird so geprüft)
ipcMain.handle('login', async () => {
  const auth = new Auth('select_account');
  const xbox = await auth.launch('electron');
  session = await xbox.getMinecraft();
  return session.profile.name;
});

ipcMain.handle('play', async (_e, { version, ram }) => {
  if (!session) throw new Error('Bitte zuerst anmelden.');
  const launcher = new Client();
  launcher.on('progress', p => send('progress', p));
  launcher.on('debug', m => send('log', m));
  launcher.on('data', m => send('log', m));
  launcher.on('close', code => send('closed', code));

  await launcher.launch({
    authorization: session.mclc(),
    root: path.join(app.getPath('appData'), '.crafthub'),
    version: { number: version, type: 'release' },
    memory: { max: `${ram}G`, min: '2G' }
  });
});
