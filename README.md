# SkyLaunch

A Minecraft launcher for Hypixel SkyBlock. Sign in with Microsoft, pick a preset, press Play — it downloads Minecraft, the mod loader, the right Java and the mods, then drops you straight onto `mc.hypixel.net`.

## What it does

- **Microsoft sign-in** in a popup window (Microsoft's own page; the launcher never sees your password). Multiple accounts, tokens encrypted with Windows' DPAPI.
- **Profiles**, each with its own mods, configs, screenshots and memory setting.
- **Presets** for both setups:
  - Modern Fabric: Skyblocker, SkyHanni, Firmament, Performance only, Empty
  - 1.8.9 Forge: NEU + SkyHanni, SkyHanni, Empty
- **Browse Modrinth** tab: search, sort, filter by category (or SkyBlock), and install the newest build or pick an exact version. Required dependencies come along automatically. Results only show mods that fit the profile's Minecraft version and loader.
- **Shaders from Modrinth**: on the Browse tab, switch to **Shaders** to search packs that work with the profile's Minecraft version. Install adds the pack plus Iris (the shader mod, which brings Sodium), and switches it on. Pick which pack is in use, or turn shaders off, under **Shader packs** on the Mods tab. Your own `.zip` packs can be dropped on the window. Modern (Fabric) profiles only; 1.8.9 shaders need OptiFine, which can't be downloaded automatically.
- **Your own files**: drag `.jar` files onto the window or use Add .jar files.
- **Looks like Windows 11**: follows your light/dark setting and uses your Windows accent colour.
- Modern presets pick the **newest Minecraft version every required mod supports**, and you can switch versions later — mods are re-fetched for the new version.
- **Java is automatic**: Java 8 for 1.8.9, whatever Mojang specifies for modern versions (Eclipse Temurin, stored inside the launcher's folder).
- **Auto-join Hypixel**, enable/disable mods with a switch, live game logs.
- **Themes**: Midnight, Dawn (orange and yellow), Violet and Light, picked in Settings. The in-game menu follows the launcher's theme.
- **SkyLaunch Client**, a built-in in-game mod for 26.2 Fabric and 1.8.9 Forge. Press Right Shift in game for FPS/CPS/ping/coords/keystrokes, a very customizable SkyBlock HUD (purse, bits, bank, health/mana bars, skill XP/h), zoom, toggle sprint & sneak, reach display, combo counter, compass, stopwatch, chat timestamps, fullbright, no view bobbing, no hurt camera, no fire overlay, crosshair, hit colour and block outline, coin tracker, speed/memory/session/day/server/saturation HUDs and vanilla HUD hiding (including the boss bar), plus a drag-and-snap HUD editor. See [client-mod/README.md](client-mod/README.md).

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

## Sharing a world with friends

Open a singleplayer world on a modern (26.2) profile, press **Right Shift → Share world**, choose the game mode and click **Start LAN World**. A few seconds later you get an address (like `brave-otter.au.e4mc.link`) and an **invite**.

- **Friends on SkyLaunch:** they click **Join a friend** in the launcher and paste the invite. SkyLaunch shows which of your mods they need, installs those exact versions into a profile named after you, and joins your world. Mods that aren't on Modrinth are listed so they can ask you for the files.
- **Friends on any other launcher:** they add the address as a server. They need the same Minecraft version and mods as you.

Sharing uses [e4mc](https://modrinth.com/mod/e4mc), a free relay that's added to modern profiles automatically. The world is online while you're playing it. It isn't available on 1.8.9.

## Updates

SkyLaunch updates itself from this repo's GitHub Releases. When it starts it checks for a newer release and downloads it in the background. Players get a **Restart to update** button in the sidebar, or the update installs by itself the next time they close SkyLaunch. It never restarts while Minecraft is running. **Check for updates** under the account button checks again by hand.

To publish an update:

1. On GitHub, open **Actions → Build → Run workflow**.
2. Type the new version in **Release as this version** (higher than the last one, e.g. `0.1.1`), then click **Run workflow**.

That run builds everything and publishes a release, and everyone's launcher picks it up. Pushing a tag like `v0.1.1` does the same. Normal pushes only build; they don't release.

The repo has to be **public** for other people's launchers to see releases.

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
