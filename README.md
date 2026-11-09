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

Der Microsoft-Login verwendet den offiziellen OAuth-Geraetecode-Ablauf. Microsoft verlangt eine registrierte App-ID; sie muss vor dem Login als `CRAFTHUB_MICROSOFT_CLIENT_ID` in der Umgebung gesetzt werden. Details stehen im Abschnitt "Microsoft-Login". Das Herunterladen des Spielclients und der Spielstart sind noch nicht enthalten.

## Starten

Voraussetzung ist Java 17 oder neuer.

```bash
java -jar CraftHub-1.0.0.jar
```

Unter Windows funktioniert dieselbe JAR-Datei mit installiertem Java, zum Beispiel per Doppelklick oder über PowerShell. Die Windows-CI erstellt außerdem einen nativen Installer mit CraftHub-Desktopverknüpfung als Workflow-Artefakt.

Der Windows-Workflow erstellt zusätzlich das Artefakt `CraftHub-Windows-Ordner`. Darin ist `CraftHub.exe` der eigenständige Installer und kann direkt aus dem ZIP gestartet werden. Das separate Artefakt `CraftHub-Windows-Portable` enthält den portablen Launcher samt Java-Laufzeit; dieses ZIP muss vollständig entpackt werden, bevor `CraftHub.exe` gestartet wird.

## Entwickeln und bauen

```bash
mvn verify
java -jar target/CraftHub-1.0.0.jar
```

Profile werden unter `%APPDATA%/CraftHub` auf Windows und unter `~/.crafthub` auf Linux und macOS gespeichert.

## Microsoft-Login

Registriere in [Microsoft Entra](https://entra.microsoft.com/) eine App für persönliche Microsoft-Konten und erlaube den Public-Client-Flow. Setze die angezeigte Anwendungs-ID vor dem Start:

```powershell
$env:CRAFTHUB_MICROSOFT_CLIENT_ID = "deine-anwendungs-id"
java -jar .\CraftHub-1.0.0.jar
```

Die Client-ID ist eine öffentliche App-Kennung, kein Passwort. CraftHub speichert keine Microsoft-Zugangsdaten oder Tokens.
