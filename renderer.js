const $ = (id) => document.getElementById(id);
const log = (t) => { const l = $('log'); l.textContent += '\n' + t; l.scrollTop = l.scrollHeight; };

(async () => {
  const versions = await window.crafthub.versions();
  $('version').innerHTML = versions.map(v => `<option>${v}</option>`).join('');
})().catch(() => log('Versionsliste konnte nicht geladen werden. Prüfe deine Internetverbindung.'));

$('login').onclick = async () => {
  try {
    const name = await window.crafthub.login();
    $('user').textContent = `Angemeldet als ${name}`;
    $('play').disabled = false;
  } catch (e) {
    log('Anmeldung fehlgeschlagen: ' + e.message);
  }
};

$('play').onclick = async () => {
  $('play').disabled = true;
  $('bar').hidden = false;
  try {
    await window.crafthub.play({ version: $('version').value, ram: $('ram').value });
  } catch (e) {
    log('Start fehlgeschlagen: ' + e.message);
    $('play').disabled = false;
  }
};

window.crafthub.on('progress', p => { if (p && p.total) $('bar').value = (p.task / p.total) * 100; });
window.crafthub.on('log', log);
window.crafthub.on('closed', () => { $('play').disabled = false; $('bar').hidden = true; log('Minecraft wurde beendet.'); });
