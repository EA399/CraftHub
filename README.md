# CraftHub 2.1 — Instances & Modrinth

German-language Electron desktop prototype for managing isolated Minecraft instance folders and downloading compatible Modrinth `.jar` files directly into an instance.

## Features in this build
- Isolated instance folders under Electron's user data directory
- Create and delete instances with Minecraft version and loader metadata
- Modrinth project search
- Install compatible Modrinth JAR files into the selected instance's `mods` directory
- List local JAR files and open an instance folder
- Windows NSIS installer build

## Build
Requirements: Node.js 22+ and npm.

```bash
npm install
npm run start
npm run build:win
```

The installer is written to `dist/CraftHub-Setup-2.1.0.exe`.

## Important limitations
- **Direct Minecraft launching is not implemented yet.** The Play button explicitly reports this rather than bypassing authentication.
- **Microsoft authentication is not implemented yet.** A proper Microsoft Entra application registration, OAuth Authorization Code + PKCE, Xbox Live/XSTS and Minecraft Services entitlement/profile flow are required. Do not place a client secret in a desktop app.
- Instance creation creates separated CraftHub-managed folders, but does not yet install the Minecraft runtime or Fabric/Forge/NeoForge/Quilt loader. Mod installation will work only when a matching Modrinth version exists; actual launching awaits loader/runtime installation.
- Modpacks, resource packs, shaders, dependency resolution, checksums, and updates are not yet installed by this prototype.
- Offline mode is limited to local instance files; authenticated offline play is not yet implemented.
- Test carefully before using important worlds. Back up saves.
