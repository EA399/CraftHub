# CraftHub

CraftHub ist ein Java-Desktop-Launcher zum Verwalten von Minecraft-Instanzen und Mods.

## Funktionen

- Offizielle Minecraft-Release-Versionen laden
- Instanzen mit Vanilla, Fabric, Forge, NeoForge oder Quilt erstellen, kopieren und löschen
- Jede offizielle Release-Version wählen, darunter Minecraft 1.8.9 und aktuelle Releases
- Modrinth nach Mods für die Version und den Loader einer Instanz durchsuchen
- Mod-Dateien in die ausgewählte Instanz laden und SHA-512-Prüfsummen kontrollieren
- Instanzordner direkt öffnen
- Eigenes CraftHub-Icon im Fenster, in der Taskleiste und in der Windows-Desktopverknüpfung

Der Microsoft-Login verwendet den offiziellen OAuth-Gerätecode-Ablauf. Microsoft verlangt eine eigene registrierte App-ID; CraftHub fragt sie beim ersten Anmelden ab und speichert sie lokal. Das Herunterladen des Spielclients und der Spielstart sind noch nicht enthalten.

## Starten

Voraussetzung ist Java 17 oder neuer.

```bash
java -jar CraftHub-1.0.0.jar
```

Unter Windows funktioniert dieselbe JAR-Datei mit installiertem Java, zum Beispiel per Doppelklick oder über PowerShell. Die Windows-CI erstellt außerdem einen nativen Installer mit CraftHub-Desktopverknüpfung als Workflow-Artefakt.

Der Windows-Workflow erstellt zusätzlich das Artefakt `CraftHub-Windows-Ordner`. ZIP vollständig entpacken und danach `CraftHub.exe` starten; `app` und `runtime` liegen direkt daneben. `CraftHub-Setup.exe` ist der optionale Installer. Das separate Artefakt `CraftHub-Windows-Portable` enthält dieselbe portable App ohne Installer.

## Entwickeln und bauen

```bash
mvn verify
java -jar target/CraftHub-1.0.0.jar
```

Profile werden unter `%APPDATA%/CraftHub` auf Windows und unter `~/.crafthub` auf Linux und macOS gespeichert.
Startfehler werden unter `%LOCALAPPDATA%/CraftHub/logs/launcher.log` protokolliert.

## Microsoft-Login

Registriere in [Microsoft Entra](https://entra.microsoft.com/) eine App. Wähle persönliche Microsoft-Konten als unterstützten Kontotyp und aktiviere unter Authentifizierung den öffentlichen Client-Flow. Beim ersten Klick auf „Mit Microsoft anmelden“ fügst du die Anwendungs-ID in CraftHub ein. Sie wird nur lokal in deinen Benutzereinstellungen gespeichert.

Die Client-ID ist eine öffentliche App-Kennung, kein Passwort. CraftHub speichert keine Microsoft-Zugangsdaten oder Tokens. Alternativ kann `CRAFTHUB_MICROSOFT_CLIENT_ID` als Umgebungsvariable gesetzt werden.
