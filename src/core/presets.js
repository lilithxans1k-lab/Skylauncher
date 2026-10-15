// Mod presets. Every mod is a Modrinth project slug, resolved at install time against the
// profile's Minecraft version and loader, so presets keep working as mods update.
//
// `required` mods decide which Minecraft version a modern preset uses (the newest version
// they all support). `optional` mods are added when a build exists for that version and
// skipped quietly when one doesn't.
//
// Only include mods Hypixel allows. Hypixel's rules: https://hypixel.net/allowed-modifications

const HYPIXEL_HOST = 'mc.hypixel.net';

const PRESETS = [
  {
    id: 'skyblocker',
    name: 'Skyblocker',
    era: 'modern',
    loader: 'fabric',
    summary: 'All-round SkyBlock helper: dungeon map, item prices, slayer and garden tools. A good first pick.',
    required: ['fabric-api', 'skyblocker-liap'],
    optional: ['sodium', 'modmenu', 'ferrite-core', 'entityculling', 'immediatelyfast'],
  },
  {
    id: 'skyhanni-modern',
    name: 'SkyHanni',
    era: 'modern',
    loader: 'fabric',
    summary: 'Huge feature set for farming, fishing, mining and events, with a detailed config menu.',
    required: ['fabric-api', 'skyhanni'],
    optional: ['sodium', 'modmenu', 'ferrite-core', 'entityculling', 'immediatelyfast'],
  },
  {
    id: 'firmament',
    name: 'Firmament',
    era: 'modern',
    loader: 'fabric',
    summary: 'Item list and recipe browser in the spirit of NotEnoughUpdates, rebuilt for new versions.',
    required: ['fabric-api', 'firmament'],
    optional: ['sodium', 'modmenu', 'ferrite-core', 'entityculling'],
  },
  {
    id: 'performance',
    name: 'Performance only',
    era: 'modern',
    loader: 'fabric',
    summary: 'No SkyBlock features, just higher FPS. Add your own mods on top.',
    required: ['fabric-api', 'sodium'],
    optional: ['modmenu', 'ferrite-core', 'entityculling', 'immediatelyfast'],
  },
  {
    id: 'legacy-neu',
    name: 'NEU + SkyHanni',
    era: 'legacy',
    loader: 'forge',
    mcVersion: '1.8.9',
    summary: 'The classic 1.8.9 setup: NotEnoughUpdates item list and profile viewer plus SkyHanni.',
    required: ['notenoughupdates'],
    optional: ['skyhanni'],
  },
  {
    id: 'legacy-skyhanni',
    name: 'SkyHanni (1.8.9)',
    era: 'legacy',
    loader: 'forge',
    mcVersion: '1.8.9',
    summary: 'SkyHanni on its own, for players who prefer 1.8.9 combat and feel.',
    required: ['skyhanni'],
    optional: [],
  },
  {
    id: 'empty-modern',
    name: 'Empty (modern Fabric)',
    era: 'modern',
    loader: 'fabric',
    summary: 'Fabric with nothing installed. Pick every mod yourself.',
    required: ['fabric-api'],
    optional: [],
  },
  {
    id: 'empty-legacy',
    name: 'Empty (1.8.9 Forge)',
    era: 'legacy',
    loader: 'forge',
    mcVersion: '1.8.9',
    summary: 'Forge 1.8.9 with nothing installed. Drop in your own jars.',
    required: [],
    optional: [],
  },
];

// The one Forge build every 1.8.9 SkyBlock mod targets.
const FORGE_189 = {
  mcVersion: '1.8.9',
  forgeVersion: '11.15.1.2318-1.8.9',
  universalUrl:
    'https://maven.minecraftforge.net/net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar',
};

const getPreset = (id) => PRESETS.find((p) => p.id === id);

module.exports = { PRESETS, getPreset, FORGE_189, HYPIXEL_HOST };
