# SkyLaunch

A Minecraft launcher for Hypixel SkyBlock. Sign in with Microsoft, pick a preset, press Play — it downloads Minecraft, the mod loader, the right Java and the mods, then drops you straight onto `mc.hypixel.net`.

## What it does

- **Microsoft sign-in** in a popup window (Microsoft's own page; the launcher never sees your password). Multiple accounts, tokens encrypted with Windows' DPAPI.
- **Profiles**, each with its own mods, configs, screenshots and memory setting.
- **Presets** for both setups:
  - Modern Fabric: Skyblocker, SkyHanni, Firmament, Performance only, Empty
  - 1.8.9 Forge: NEU + SkyHanni, SkyHanni, Empty
- **Browse Modrinth** tab: search, sort, filter by category (or SkyBlock), and install the newest build or pick an exact version. Required dependencies come along automatically. Results only show mods that fit the profile's Minecraft version and loader.
- **Your own files**: drag `.jar` files onto the window or use Add .jar files.
- **Looks like Windows 11**: follows your light/dark setting and uses your Windows accent colour.
- Modern presets pick the **newest Minecraft version every required mod supports**, and you can switch versions later — mods are re-fetched for the new version.
- **Java is automatic**: Java 8 for 1.8.9, whatever Mojang specifies for modern versions (Eclipse Temurin, stored inside the launcher's folder).
- **Auto-join Hypixel**, enable/disable mods with a switch, live game logs.
- **SkyLaunch Client**, a built-in in-game mod for 26.2 Fabric and 1.8.9 Forge. Press Right Shift in game for FPS/CPS/ping/coords/keystrokes, a very customizable SkyBlock HUD (purse, bits, bank, health/mana bars, skill XP/h), zoom, toggle sprint, fullbright, crosshair, hit colour and block outline, plus a drag-and-snap HUD editor. See [client-mod/README.md](client-mod/README.md).

## Run it (Windows 11)

1. Install **Node.js 22 LTS or newer** from <https://nodejs.org>.
2. Unzip this folder, open a terminal in it, then:

   ```
   npm install
   npm start
   ```

3. To build an installer (`dist\SkyLaunch Setup 0.1.0.exe`):

   ```
   npm run dist
   ```

`npm run check` runs offline sanity checks (syntax + the Modrinth version/dependency logic).

To include the in-game client, build it first with `npm run build:client` (needs JDK 25 and JDK 17, see client-mod/README.md). Or push to GitHub and let `.github/workflows/build.yml` build the mods and the installer for you.

## Where things live

`%APPDATA%\SkyLaunch\`

| Folder | What's in it |
| --- | --- |
| `minecraft\` | Shared game files (versions, libraries, assets) used by every profile |
| `instances\<profile>\` | One game folder per profile: `mods`, `config`, `screenshots`, `logs` |
| `runtime\java-<n>\` | Java the launcher downloaded |
| `accounts.json` | Account names + encrypted refresh tokens |

## Code map

| File | Job |
| --- | --- |
| `src/main.js` | Electron window and the IPC handlers the UI calls |
| `src/preload.js` | The safe bridge exposed to the UI as `window.sky` |
| `src/core/presets.js` | Preset definitions (Modrinth slugs) — edit this to add your own presets |
| `src/core/modrinth.js` | Version lookup, dependency resolution, search |
| `src/core/instances.js` | Profiles and their mods |
| `src/core/loaders.js` | Fabric (meta.fabricmc.net) and Forge 1.8.9 install |
| `src/core/java.js` | Picks and downloads the right Java |
| `src/core/auth.js` | Microsoft sign-in via `msmc` |
| `src/core/launch.js` | Puts it together and starts the game via `minecraft-launcher-core` |
| `src/renderer/` | The UI (plain HTML/CSS/JS, no framework) |
| `src/core/clientmod.js` | Copies the built SkyLaunch Client jar into matching profiles |
| `client-mod/` | The in-game client: shared `core/`, plus `fabric/` (26.2) and `forge189/` (1.8.9) |

### Adding a preset

Add an entry to `PRESETS` in `src/core/presets.js`. Mods are Modrinth slugs (the bit after `modrinth.com/mod/`). `required` mods decide the Minecraft version; `optional` ones are skipped quietly when a build doesn't exist.

## Things to know

- **Hypixel rules.** Only ship mods Hypixel allows: <https://hypixel.net/allowed-modifications>. Macros, auto-clickers and ESP-type mods get accounts banned; keep them out of presets.
- **Releasing it publicly.** Sign-in uses msmc's default Microsoft app, which is fine for you and friends. Before distributing widely, register your own Azure app, request Minecraft API access from Mojang, and pass your client ID to `new Auth({ client_id: '…', redirect: '…' })` in `src/core/auth.js`.
- **Newest Minecraft versions.** `minecraft-launcher-core` was last released in Dec 2024. If a brand-new Minecraft release changes its file format and fails to start, pick a slightly older version in Profile settings, or update the library.
- **1.8.9 Forge** uses Forge 11.15.1.2318, the build every 1.8.9 SkyBlock mod targets.
