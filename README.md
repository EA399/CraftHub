# CraftHub

A modern Electron desktop client for discovering Minecraft mods and modpacks from the [Modrinth](https://modrinth.com) community.

## Features

- Search Modrinth projects and modpacks using the Modrinth API
- Save favorites locally in the Electron browser profile
- Open official project pages in your default browser
- Dark desktop UI with responsive layout
- Native Electron menu bar disabled and scrollbars visually hidden
- GitHub Actions workflow to build a Windows installer

## Requirements

- Node.js 22 or later
- npm
- Internet connection for Modrinth search

## Run locally

```bash
npm install
npm start
```

## Build a Windows installer

```bash
npm install
npm run build:win
```

The installer is created under `dist/`.

You can also open the **Actions** tab in GitHub and run **Build CraftHub**. The resulting installer is uploaded as a workflow artifact.

## Create a GitHub Release

Push a version tag, for example:

```bash
git tag v2.0.0
git push origin v2.0.0
```

The release workflow builds the Windows installer and attaches it to the GitHub Release.

## Current limitations

CraftHub currently discovers projects through Modrinth and opens their official project pages. It does **not** yet install mods into a Minecraft instance, manage loaders, authenticate Microsoft accounts, or launch Minecraft. Those features need additional implementation and testing.

## Project structure

```text
CraftHub/
├── .github/
│   └── workflows/
│       ├── build.yml
│       └── release.yml
├── index.html
├── main.js
├── package.json
├── LICENSE
└── .gitignore
```

## License

MIT — see [LICENSE](LICENSE).
