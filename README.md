# CraftHub 2.2.0

Electron-Prototyp für lokale Minecraft-Instances und Modrinth-Projektsuche.

## Starten
- Node.js LTS installieren
- In diesem Ordner `npm install`
- `npm start`

## Windows-Installer bauen
`npm run build:win`

Der Installer liegt anschließend unter `dist/`. `--publish never` verhindert, dass electron-builder ein GitHub-Release benötigt.

## Enthalten
- Lokale Instances anlegen, kopieren, löschen und Ordner öffnen
- Minecraft-Version und Loader-Metadaten speichern
- Modrinth-Projekte durchsuchen
- Kompatible Mod-JARs für die angegebene Minecraft-Version und den Loader in den `mods`-Ordner laden

## Noch nicht fertig / wichtig
- **Modrinth OAuth:** noch nicht implementiert. Für echten Kontologin muss eine eigene Modrinth-OAuth-App registriert und ein sicherer OAuth-Redirect-Flow ergänzt werden. CraftHub fragt nicht nach deinem Passwort.
- **Minecraft-Login/Start:** nicht implementiert; Instances sind derzeit Ordner plus Metadaten. Minecraft, Java und Mod-Loader werden nicht installiert.
- Modpacks, Shader und Resourcepacks werden angezeigt/weitergeleitet, aber nicht automatisch installiert.
- Keine vollständige Abhängigkeitsauflösung, Versionsverwaltung oder Integritätsprüfung.

CraftHub ist unabhängig und nicht mit Modrinth verbunden. Bitte nicht als fertigen Minecraft-Launcher veröffentlichen, bevor die fehlenden Funktionen ergänzt und getestet wurden.
