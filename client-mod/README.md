# SkyLaunch Client (in-game mod)

The in-game half of SkyLaunch: press **Right Shift** in game to open a menu of built-in mods, change their settings, and drag the HUD around. It looks like the launcher: neutral panels, one accent colour, dark or light.

It comes in two builds that share one codebase:

| Folder | Minecraft | Loader | Java to run Gradle |
| --- | --- | --- | --- |
| `fabric/` | 26.2 | Fabric (Loom 1.18, Fabric API 0.161.0+26.2) | JDK 25 |
| `forge189/` | 1.8.9 | Forge 11.15.1.2318 (Essential's Loom) | JDK 17 or 21 |
| `core/` | both | plain Java 8, no Minecraft code | any JDK 8+ |

`core/` holds the menu, HUD editor, every mod, the settings file and the SkyBlock parsers. Each version folder only has a thin layer: how to draw a rectangle, read the scoreboard, open a screen, and the few hooks into the game (zoom, fullbright, crosshair and so on).

## What's in the menu

- **HUD**: FPS, CPS, ping, coordinates (with facing), clock, keystrokes, speed, memory, session timer, compass bar, stopwatch (pick your own start/stop and reset keys), day counter, server address, saturation, and *Hide vanilla HUD* (hearts, armor, hunger, XP bar, sidebar, boss bar)
- **SkyBlock**
  - *SkyBlock info*: purse/piggy, bits, bank, motes, copper, location, server, profile, SkyBlock date and time. Each line can be switched on or off, with full or compact numbers (12.4M) and your own coin and bits colours.
  - *Health & mana bars*: health, absorption, mana, overflow mana and defense from the action bar, either stacked or side by side. Bar width, height and every colour are adjustable.
  - *Coin tracker*: coins made this session and per hour from your purse. It can count gains only, ignores profile switches, and has an optional reset key.
  - *Skill tracker*: progress bar, XP per hour, time to the next level, and XP this session, for your latest skill or all recent ones. It hides itself when you go idle.
- **Combat & QoL**: armor status with durability, potion effects, zoom (hold C, scroll to adjust), toggle sprint & sneak (V, sneak key optional), reach display, combo counter, chat timestamps
- **Visual**: fullbright, custom crosshair (cross, dot, circle, T), hit colour, block outline, no view bobbing (on by default), no hurt camera, no fire overlay
- **Share world** (in the menu header, singleplayer on 26.2): opens your world to friends through e4mc and copies an invite with your exact mod list. See the main README.
- **Appearance**: theme (Midnight, Dawn, Violet or Light, matching the launcher by default), an optional custom accent colour, the menu key, background blur behind menus (26.2), and the default look for every HUD element (value and label colours, background, rounded corners, padding, text shadow)

Every HUD element also has its own size, background, shadow and colour overrides. **Edit HUD** lets you drag elements, scroll to resize, and right-click for settings. Elements snap to screen edges, the centre lines and each other.

Settings are saved to `config/skylaunch-client.json` in each profile.

## Building

From the launcher's root folder:

```
npm run test:client     # compiles core/ as Java 8 and runs its tests
npm run build:client    # builds both mods, copies jars to resources/client/
```

The two builds need different Java versions to run Gradle. Install both and point at them:

```
set SKYLAUNCH_JDK25=C:\Program Files\Eclipse Adoptium\jdk-25...
set SKYLAUNCH_JDK17=C:\Program Files\Eclipse Adoptium\jdk-17...
npm run build:client
```

You can also build one at a time: `npm run build:client -- fabric` or `npm run build:client -- forge`.

**The easy way is GitHub.** Push the repo and `.github/workflows/build.yml` builds both mods and the Windows installer, with the mods bundled in. Download the result from the run's Artifacts.

Once the jars are in `resources/client/`, the launcher copies the right one into every 26.2 Fabric or 1.8.9 Forge profile, and **SkyLaunch Client** appears under "Built in" on the Mods tab with an on/off switch. `npm run dist` packs them into the installer.

## First build: what to expect

None of this has been compiled against Minecraft yet. My workspace couldn't reach Mojang's or Fabric's servers, so only `core/` has been built and tested so far.

- `core/` compiles as Java 8 and passes 61 tests (number formats, JSON, scoreboard, tab list and action bar parsing).
- The 26.2 calls were checked against Fabric API 26.2 and the current Skyblocker, Odin, Zoomify and Gamma Utils sources. Expect at most a handful of small compile errors, most likely in `FabricGame.java` or `ScreenWrapper.java`. Paste the first error and it's usually a one-line fix.
- **Hit colour on 26.2** looks up the game's overlay texture while running. If the game has moved it, that feature switches itself off and logs one line, and nothing else is affected.
- **Block outline on 26.2** draws a coloured fill over the block and can keep or hide the vanilla lines, but it can't recolour the lines themselves. On 1.8.9 it can do both.

## Hypixel rules

Everything here is display or quality-of-life: HUD elements, zoom, fullbright, crosshair, toggle sprint/sneak, reach and combo displays. That's the same category Lunar and Badlion ship. Nothing automates gameplay. Check https://hypixel.net/allowed-modifications if you add features.

## Credits

- `forge189/` is based on the public-domain Forge 1.8.9 template by Linnea Gräf (`AutoDiscoveryMixinPlugin` is hers).
- `fabric/` follows FabricMC's `fabric-example-mod` (branch 26.2).
