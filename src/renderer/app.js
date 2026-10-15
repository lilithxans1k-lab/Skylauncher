/* global sky */
'use strict';

// ---------- tiny DOM helper (no innerHTML, so text from Modrinth can't inject markup)
function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k === 'style') el.style.cssText = v; // CSSOM, allowed under the page's CSP
    else if (k.startsWith('on')) el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k in el && typeof v !== 'string') el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) if (c != null && c !== false) el.append(c.nodeType ? c : String(c));
  return el;
}
const $ = (id) => document.getElementById(id);

const state = {
  presets: [],
  instances: [],
  selectedId: null,
  tab: 'mods',
  account: { active: null, accounts: [] },
  settings: {},
  game: { id: null, state: 'stopped' },
  busy: null, // { label, fraction }
  logs: [],
  mods: [],
  shaders: { packs: [], active: null, enabled: false, iris: false },
  browse: { type: 'mod', query: '', sort: 'relevance', category: '', skyblock: false, hits: [], total: 0, loading: false, error: null, forId: null },
};

// ---------- feedback
let toastTimer;
function toast(msg, isError = false) {
  const t = $('toast');
  t.textContent = msg;
  t.className = `toast show${isError ? ' error' : ''}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.className = 'toast'), isError ? 7000 : 3000);
}

async function attempt(fn, { busyLabel } = {}) {
  if (busyLabel) { state.busy = { label: busyLabel, fraction: 0 }; renderHeader(); }
  try {
    return await fn();
  } catch (e) {
    toast(e.message, true);
    return undefined;
  } finally {
    if (busyLabel) { state.busy = null; renderHeader(); }
  }
}

const current = () => state.instances.find((i) => i.id === state.selectedId) || null;
const presetOf = (inst) => state.presets.find((p) => p.id === inst.presetId);
const loaderName = (l) => (l === 'fabric' ? 'Fabric' : 'Forge');
const fmtCount = (n) => (n >= 1e6 ? `${(n / 1e6).toFixed(1)}M` : n >= 1e3 ? `${Math.round(n / 1e3)}k` : String(n));
function fmtAgo(iso) {
  const days = Math.floor((Date.now() - new Date(iso)) / 864e5);
  if (days < 1) return 'today';
  if (days < 30) return `${days} day${days === 1 ? '' : 's'} ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months} month${months === 1 ? '' : 's'} ago`;
  return `${Math.floor(months / 12)} year${months < 24 ? '' : 's'} ago`;
}

// ---------- data
async function refreshInstances(selectId) {
  state.instances = await sky.instances.list();
  if (selectId) state.selectedId = selectId;
  if (!current()) state.selectedId = state.instances[0] ? state.instances[0].id : null;
  renderProfiles();
  renderHeader();
  await renderPanel();
}

async function refreshShaders() {
  const inst = current();
  state.shaders = inst ? await sky.shaders.list(inst.id) : { packs: [], active: null, enabled: false, iris: false };
}

async function refreshMods() {
  const inst = current();
  state.mods = inst ? await sky.mods.list(inst.id) : [];
}

// ---------- profile avatars
// A profile's picture is the icon of its main mod (Skyblocker, SkyHanni...), or a tinted letter.
const LIBRARY_MODS = /fabric api|fabric-api|sodium|iris|e4mc|mod menu|modmenu|ferrite|entity ?culling|immediatelyfast|lithium|cloth|architectury|yacl|kotlin/i;
function profileIconUrl(inst) {
  const main = (inst.mods || []).find((m) => m.icon && !LIBRARY_MODS.test(`${m.title} ${m.file}`));
  return main ? main.icon : null;
}
function hueOf(text) {
  let x = 0;
  for (const ch of text) x = (x * 31 + ch.charCodeAt(0)) >>> 0;
  return x % 360;
}
function avatar(inst, size) {
  const url = profileIconUrl(inst);
  const box = h('span', { class: `avatar avatar-${size}`, style: `--hue:${hueOf(inst.name)}` });
  if (url) {
    box.append(h('img', { src: url, alt: '', onerror: (e) => { e.target.remove(); box.textContent = inst.name.slice(0, 1).toUpperCase(); } }));
  } else {
    box.textContent = inst.name.slice(0, 1).toUpperCase();
  }
  return box;
}
function fmtPlaytime(ms) {
  const m = Math.floor((ms || 0) / 60000);
  if (m < 1) return 'Not played yet';
  if (m < 60) return `${m}m played`;
  return `${Math.floor(m / 60)}h ${m % 60}m played`;
}

// ---------- sidebar
function renderProfiles() {
  $('profiles').replaceChildren(
    ...state.instances.map((inst) => {
      const live = state.game.id === inst.id && state.game.state !== 'stopped';
      return h('button', {
        class: 'profile-item',
        'aria-current': String(inst.id === state.selectedId),
        onclick: async () => {
          if (state.selectedId === inst.id) return;
          state.selectedId = inst.id;
          renderProfiles();
          renderHeader();
          await renderPanel();
        },
      },
      avatar(inst, 'sm'),
      h('span', { class: 'profile-text' },
        h('span', { class: 'name', text: inst.name }),
        h('span', { class: `sub${live ? ' live' : ''}`, text: live ? 'Playing now' : `${inst.mcVersion} ${loaderName(inst.loader)}` })));
    }),
  );
}

function renderAccount() {
  const box = $('account');
  const { active, accounts } = state.account;
  const acct = accounts.find((a) => a.uuid === active);
  if (!acct) {
    box.replaceChildren(h('button', { class: 'account-btn', onclick: signIn },
      h('span', { class: 'ph' }), h('span', { class: 'who' }, h('span', { text: 'Sign in' }), h('span', { text: 'Microsoft account' }))));
    return;
  }
  box.replaceChildren(h('button', { class: 'account-btn', onclick: toggleAccountMenu, 'aria-haspopup': 'menu' },
    h('img', { src: `https://mc-heads.net/avatar/${acct.uuid}/48`, alt: '', onerror: (e) => e.target.replaceWith(h('span', { class: 'ph' })) }),
    h('span', { class: 'who' }, h('span', { text: acct.name }), h('span', { text: accounts.length > 1 ? `${accounts.length} accounts` : 'Microsoft account' }))));
}

const closeMenus = () => document.querySelectorAll('.menu').forEach((m) => m.remove());
document.addEventListener('click', (e) => { if (!e.target.closest('.menu')) closeMenus(); });

function toggleAccountMenu(e) {
  e.stopPropagation();
  if (document.querySelector('.menu')) return closeMenus();
  const { active, accounts } = state.account;
  $('account').append(h('div', { class: 'menu', role: 'menu' },
    ...accounts.map((a) => h('button', {
      role: 'menuitem', class: a.uuid === active ? 'active' : '',
      onclick: async () => { closeMenus(); state.account = await sky.auth.switchTo(a.uuid); renderAccount(); },
    }, a.name)),
    h('hr'),
    h('button', { role: 'menuitem', onclick: () => { closeMenus(); signIn(); } }, 'Add account'),
    h('button', {
      role: 'menuitem',
      onclick: async () => { closeMenus(); state.account = await sky.auth.signOut(active); renderAccount(); renderHeader(); toast('Signed out'); },
    }, 'Sign out')));
}

async function signIn() {
  const res = await attempt(() => sky.auth.signIn());
  if (!res) return;
  state.account = res;
  renderAccount();
  renderHeader();
  toast(`Signed in as ${res.accounts.find((a) => a.uuid === res.active).name}`);
}

// ---------- header
function renderHeader() {
  const box = $('header');
  const inst = current();
  if (!inst) { box.replaceChildren(); return; }
  const preset = presetOf(inst);
  const isThis = state.game.id === inst.id;
  const running = isThis && state.game.state === 'running';
  const preparing = isThis && state.game.state === 'preparing';
  const otherRunning = state.game.id && !isThis && state.game.state !== 'stopped';

  let button;
  if (running) button = h('button', { class: 'btn big', onclick: () => sky.game.stop() }, icon('stop', 14), 'Close game');
  else if (!state.account.active) button = h('button', { class: 'btn accent big', onclick: signIn, text: 'Sign in to play' });
  else button = h('button', { class: `btn accent big play-btn${preparing ? ' busy' : ''}`, disabled: preparing || otherRunning || !!state.busy, onclick: play }, icon('play', 16), preparing ? 'Starting' : 'Play');

  let below;
  if (state.busy) {
    below = h('div', { class: 'progress' },
      h('div', { class: 'bar' }, h('div', { class: 'fill', style: `width:${Math.round(state.busy.fraction * 100)}%` })),
      h('span', { class: 'label', text: state.busy.label }));
  } else if (running) below = h('span', { class: 'hint-line', text: 'Running' });
  else if (otherRunning) below = h('span', { class: 'hint-line', text: 'Another profile is running' });
  else if (inst.autoJoin) below = h('span', { class: 'hint-line', text: 'Joins mc.hypixel.net' });

  const meta = [`Minecraft ${inst.mcVersion}`, loaderName(inst.loader), preset ? preset.name : null].filter(Boolean).join('  ·  ');
  const mods = inst.mods || [];
  const shaders = inst.shaders || [];
  const stat = (ic, text) => h('span', { class: 'stat' }, icon(ic, 14), text);
  const stats = h('div', { class: 'stats-row' },
    stat('box', `${mods.length} mod${mods.length === 1 ? '' : 's'}`),
    inst.loader === 'fabric' && stat('sun', `${shaders.length} shader${shaders.length === 1 ? '' : 's'}`),
    stat('clock', fmtPlaytime(inst.playMs)),
    inst.lastPlayed && h('span', { class: 'stat muted', text: `Last played ${fmtAgo(new Date(inst.lastPlayed).toISOString())}` }));
  // A row of the profile's mod icons, so the header shows what's actually in it.
  const icons = mods.filter((m) => m.icon && !LIBRARY_MODS.test(`${m.title} ${m.file}`)).slice(0, 7);
  const strip = icons.length > 1 && h('div', { class: 'mod-strip', 'aria-hidden': 'true' },
    ...icons.map((m) => h('img', { src: m.icon, alt: '', title: m.title, onerror: (e) => e.target.remove() })),
    mods.length > icons.length && h('span', { class: 'more', text: `+${mods.length - icons.length}` }));
  box.style.setProperty('--hue', hueOf(inst.name));
  box.replaceChildren(
    h('div', { class: 'hero-glow', 'aria-hidden': 'true' }),
    avatar(inst, 'lg'),
    h('div', { class: 'title' },
      h('h1', { text: inst.name }),
      h('div', { class: 'meta', text: meta }),
      stats,
      strip),
    h('div', { class: 'play-area' }, button, below));
}

async function play() {
  const inst = current();
  state.logs = [];
  state.game = { id: inst.id, state: 'preparing' };
  renderHeader();
  renderProfiles();
  try {
    await sky.game.launch(inst.id);
  } catch (e) {
    state.game = { id: null, state: 'stopped' };
    state.busy = null;
    renderHeader();
    renderProfiles();
    toast(e.message, true);
  }
}

// ---------- tabs
function selectTab(tab) {
  state.tab = tab;
  document.querySelectorAll('#tabs [role=tab]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === tab)));
  return renderPanel();
}
document.querySelectorAll('#tabs [role=tab]').forEach((btn) => btn.addEventListener('click', () => selectTab(btn.dataset.tab)));

async function renderPanel() {
  const panel = $('panel');
  panel.classList.remove('drop');
  const inst = current();
  $('tabs').hidden = !inst;
  if (!inst) {
    panel.replaceChildren(h('div', { class: 'welcome' },
      h('h2', { text: 'Create your first profile' }),
      h('p', { text: 'A profile is one Minecraft setup with its own mods. Start from a SkyBlock preset or an empty one.' }),
      h('button', { class: 'btn accent', onclick: openNewProfile, text: 'New profile' })));
    return;
  }
  if (state.tab === 'mods') return renderMods(panel, inst);
  if (state.tab === 'browse') return renderBrowse(panel, inst);
  if (state.tab === 'settings') return renderSettings(panel, inst);
  return renderLogs(panel);
}

// ---------- Mods tab
async function renderMods(panel, inst) {
  await refreshMods();
  await refreshShaders();
  const preset = presetOf(inst);
  let clientStatus = 'not-built';
  try { clientStatus = await sky.mods.clientStatus(inst.id); } catch { /* shown as unavailable */ }
  const toolbar = h('div', { class: 'toolbar' },
    h('button', { class: 'btn accent', onclick: () => selectTab('browse') }, icon('compass', 15), 'Browse Modrinth'),
    h('button', { class: 'btn', onclick: async () => {
      const added = await attempt(() => sky.mods.pickFiles(inst.id));
      if (added && added.length) { toast(`Added ${added.length} file${added.length === 1 ? '' : 's'}`); renderPanel(); }
    } }, icon('upload', 15), 'Add .jar files'),
    h('button', { class: 'btn quiet', onclick: () => sky.instances.openFolder(inst.id, 'mods') }, icon('folder', 15), 'Open folder'),
    h('span', { class: 'spacer' }),
    preset && h('button', {
      class: 'btn quiet',
      onclick: async () => {
        const r = await attempt(() => sky.instances.installPreset(inst.id), { busyLabel: 'Updating preset mods' });
        if (r) { toast('Preset mods are up to date'); await refreshInstances(); }
      },
      text: inst.presetInstalledAt ? 'Update preset mods' : 'Install preset mods',
    }));

  const groups = [['preset', `From ${preset ? preset.name : 'preset'}`], ['modrinth', 'From Modrinth'], ['local', 'Your files']];
  const sections = [h('div', { class: 'section-title', text: 'Built in' }), h('ul', { class: 'list' }, clientRow(inst, clientStatus))];
  for (const [src, title] of groups) {
    const list = state.mods.filter((m) => m.source === src);
    if (!list.length) continue;
    sections.push(h('div', { class: 'section-title', text: `${title} (${list.length})` }), h('ul', { class: 'list' }, ...list.map((m) => modRow(inst, m))));
  }
  if (sections.length === 2) {
    sections.push(h('div', { class: 'empty' },
      h('strong', { text: inst.presetInstalledAt || !preset ? 'No mods yet' : 'Preset mods install when you press Play' }),
      'Browse Modrinth, or drag .jar files here.'));
  }
  sections.push(...shaderSection(inst));
  panel.replaceChildren(toolbar, ...sections,
    h('p', { class: 'note' }, 'On Hypixel, only use mods Hypixel allows; macros and auto-clickers get accounts banned. ',
      h('button', { class: 'link', onclick: () => sky.app.openExternal('https://hypixel.net/allowed-modifications'), text: 'Allowed mods' })));
}

// Shader packs (Iris) for this profile.
function shaderSection(inst) {
  const sh = state.shaders;
  const title = h('div', { class: 'section-title section-row' },
    h('span', { text: sh.packs.length ? `Shader packs (${sh.packs.length})` : 'Shader packs' }),
    inst.loader === 'fabric' && h('span', { class: 'section-actions' },
      sh.enabled && h('button', { class: 'link', text: 'Turn shaders off', onclick: async () => { await attempt(() => sky.shaders.use(inst.id, null)); renderPanel(); } }),
      h('button', { class: 'link', text: 'Browse shaders', onclick: () => { state.browse.type = 'shader'; state.browse.category = ''; state.browse.forId = null; selectTab('browse'); } }),
      h('button', { class: 'link', text: 'Add .zip', onclick: async () => {
        const added = await attempt(() => sky.shaders.pickFiles(inst.id));
        if (added && added.length) { toast(`Added ${added.length} shader pack${added.length === 1 ? '' : 's'}`); renderPanel(); }
      } })));
  if (inst.loader !== 'fabric') {
    return [title, h('p', { class: 'note shader-note', text: "Shaders on 1.8.9 need OptiFine, which SkyLaunch can't download for you. Modern profiles get shaders through Iris instead." })];
  }
  if (!sh.packs.length) {
    return [title, h('p', { class: 'note shader-note', text: 'No shader packs yet. Browse shaders, or drag a shader .zip here. Iris (the shader mod) is added for you.' })];
  }
  return [title, h('ul', { class: 'list' }, ...sh.packs.map((p) => {
    const inUse = sh.enabled && sh.active === p.file;
    return h('li', { class: `row${inUse ? '' : ' off-soft'}` },
      p.icon ? h('img', { class: 'icon', src: p.icon, alt: '' }) : h('span', { class: 'icon' }),
      h('div', { class: 'main-col' },
        h('div', { class: 't', text: p.title }),
        h('div', { class: 's', text: p.version || p.file, title: p.file })),
      inUse
        ? h('span', { class: 'in-use', text: 'In use' })
        : h('button', { class: 'btn', text: 'Use', onclick: async () => { await attempt(() => sky.shaders.use(inst.id, p.file)); toast(`Using ${p.title}`); renderPanel(); } }),
      h('button', {
        class: 'x', 'aria-label': `Remove ${p.title}`, title: 'Remove',
        onclick: async () => { await attempt(() => sky.shaders.remove(inst.id, p.file)); renderPanel(); },
      }, '×'));
  }))];
}

// The SkyLaunch in-game client: Right Shift menu, HUD editor and SkyBlock HUD.
function clientRow(inst, status) {
  const sub = {
    on: 'Press Right Shift in game for mods and the HUD editor.',
    off: 'Turned off for this profile.',
    'wrong-version': `Built for Minecraft 26.2 Fabric and 1.8.9 Forge. Switch this profile to 26.2 in Settings to use it.`,
    'not-built': 'Not included in this copy of SkyLaunch. See client-mod/README.md to build it.',
  }[status];
  const usable = status === 'on' || status === 'off';
  return h('li', { class: `row${status === 'on' ? '' : ' off'}` },
    h('span', { class: 'icon brand-icon', text: 'S' }),
    h('div', { class: 'main-col' },
      h('div', { class: 't', text: 'SkyLaunch Client' }),
      h('div', { class: 's', text: sub, title: sub })),
    usable
      ? h('label', { class: 'switch', title: status === 'on' ? 'Turn off' : 'Turn on' },
        h('input', {
          type: 'checkbox', checked: status === 'on', 'aria-label': 'SkyLaunch Client enabled',
          onchange: async (e) => {
            await attempt(() => sky.instances.update(inst.id, { clientMod: e.target.checked }));
            state.instances = await sky.instances.list();
            renderPanel();
          },
        }),
        h('span'))
      : h('span'),
    h('span'));
}

function modRow(inst, m) {
  return h('li', { class: `row${m.enabled ? '' : ' off'}` },
    m.icon ? h('img', { class: 'icon', src: m.icon, alt: '' }) : h('span', { class: 'icon' }),
    h('div', { class: 'main-col' },
      h('div', { class: 't', text: m.title }),
      h('div', { class: 's', text: m.version || m.file, title: m.file })),
    h('label', { class: 'switch', title: m.enabled ? 'Turn off' : 'Turn on' },
      h('input', {
        type: 'checkbox', checked: m.enabled, 'aria-label': `${m.title} enabled`,
        onchange: async (e) => { await attempt(() => sky.mods.setEnabled(inst.id, m.file, e.target.checked)); renderPanel(); },
      }),
      h('span')),
    h('button', {
      class: 'x', 'aria-label': `Remove ${m.title}`, title: 'Remove',
      onclick: async () => { await attempt(() => sky.mods.remove(inst.id, m.file)); renderPanel(); },
    }, '×'));
}

const panelEl = $('panel');
panelEl.addEventListener('dragover', (e) => {
  if (!current() || !['mods', 'browse'].includes(state.tab)) return;
  e.preventDefault();
  panelEl.classList.add('drop');
});
panelEl.addEventListener('dragleave', (e) => { if (!panelEl.contains(e.relatedTarget)) panelEl.classList.remove('drop'); });
panelEl.addEventListener('drop', async (e) => {
  e.preventDefault();
  panelEl.classList.remove('drop');
  const inst = current();
  if (!inst) return;
  const all = [...e.dataTransfer.files].map((f) => sky.pathForFile(f));
  const jars = all.filter((p) => /\.jar$/i.test(p));
  const zips = all.filter((p) => /\.zip$/i.test(p));
  if (!jars.length && !zips.length) return toast('Drop .jar mods or .zip shader packs.', true);
  if (zips.length && inst.loader !== 'fabric') return toast('Shader packs need a modern (Fabric) profile.', true);
  let count = 0;
  if (jars.length) { const a = await attempt(() => sky.mods.addFiles(inst.id, jars)); if (a) count += a.length; }
  if (zips.length) { const a = await attempt(() => sky.shaders.addFiles(inst.id, zips)); if (a) count += a.length; }
  if (count) { toast(`Added ${count} file${count === 1 ? '' : 's'}`); selectTab('mods'); }
});

// ---------- Browse tab
const CATEGORIES = [
  ['', 'All'], ['utility', 'Utility'], ['optimization', 'Performance'], ['decoration', 'Visual'],
  ['social', 'Social'], ['game-mechanics', 'Gameplay'], ['library', 'Libraries'],
];
const SHADER_CATEGORIES = [
  ['', 'All'], ['vanilla-like', 'Vanilla-like'], ['semi-realistic', 'Realistic'], ['fantasy', 'Fantasy'],
  ['cartoon', 'Cartoon'], ['potato', 'Low-end PCs'],
];
const SORTS = [['relevance', 'Relevance'], ['downloads', 'Most downloaded'], ['updated', 'Recently updated'], ['newest', 'Newest']];
let searchTimer;
let searchSeq = 0;

async function runSearch({ append = false } = {}) {
  const inst = current();
  const b = state.browse;
  const seq = ++searchSeq;
  b.loading = true;
  b.error = null;
  if (!append) { b.hits = []; b.forId = inst.id; b.forType = b.type; }
  drawBrowseResults();
  try {
    const q = `${b.skyblock && b.type === 'mod' ? 'skyblock ' : ''}${b.query}`.trim();
    const res = await sky.mods.search(inst.id, q, { type: b.type, sort: b.sort, category: b.category, offset: append ? b.hits.length : 0 });
    if (seq !== searchSeq) return;
    b.hits = append ? [...b.hits, ...res.hits] : res.hits;
    b.total = res.total;
  } catch (e) {
    if (seq === searchSeq) b.error = e.message;
  } finally {
    if (seq === searchSeq) { b.loading = false; drawBrowseResults(); }
  }
}

async function renderBrowse(panel, inst) {
  const b = state.browse;
  await refreshMods();
  await refreshShaders();
  const setType = (type) => {
    if (b.type === type) return;
    b.type = type;
    b.category = '';
    b.hits = [];
    b.forId = null;
    renderPanel();
  };
  const seg = h('div', { class: 'seg', role: 'tablist', 'aria-label': 'What to browse' },
    h('button', { role: 'tab', 'aria-selected': String(b.type === 'mod'), onclick: () => setType('mod'), text: 'Mods' }),
    h('button', { role: 'tab', 'aria-selected': String(b.type === 'shader'), onclick: () => setType('shader'), text: 'Shaders' }));
  if (b.type === 'shader' && inst.loader !== 'fabric') {
    panel.replaceChildren(seg, h('div', { class: 'empty' },
      h('strong', { text: 'Shaders need a modern profile' }),
      "On 1.8.9, shaders need OptiFine, which SkyLaunch can't download for you. Make a modern (Fabric) profile and SkyLaunch handles shaders through Iris."));
    return;
  }
  const search = h('input', {
    class: 'input', type: 'search', value: b.query, placeholder: b.type === 'shader' ? 'Search shader packs' : 'Search Modrinth',
    'aria-label': 'Search Modrinth',
    oninput: (e) => { b.query = e.target.value; clearTimeout(searchTimer); searchTimer = setTimeout(() => runSearch(), 300); },
  });
  const sort = h('select', { class: 'input', 'aria-label': 'Sort by', onchange: (e) => { b.sort = e.target.value; runSearch(); } },
    ...SORTS.map(([v, l]) => h('option', { value: v, selected: v === b.sort, text: l })));
  const chip = (label, pressed, onclick) => h('button', { class: 'chip', 'aria-pressed': String(pressed), onclick, text: label });
  const cats = b.type === 'shader' ? SHADER_CATEGORIES : CATEGORIES;
  const chips = h('div', { class: 'chips' },
    b.type === 'mod' && chip('SkyBlock', b.skyblock, () => { b.skyblock = !b.skyblock; renderPanel(); runSearch(); }),
    ...cats.map(([v, l]) => chip(l, b.category === v, () => { b.category = v; renderPanel(); runSearch(); })));

  panel.replaceChildren(
    seg,
    h('div', { class: 'search-bar' }, search, sort),
    chips,
    h('div', { id: 'browseResults' }));
  drawBrowseResults();
  if (b.forId !== inst.id || b.forType !== b.type || (!b.hits.length && !b.loading && !b.error)) runSearch();
  if (!b.query) search.focus();
}

function drawBrowseResults() {
  const box = $('browseResults');
  const inst = current();
  if (!box || !inst) return;
  const b = state.browse;
  const shaders = b.type === 'shader';
  const owned = shaders ? state.shaders.packs : state.mods;
  const installed = new Map(owned.filter((m) => m.projectId).map((m) => [m.projectId, m]));

  const parts = [];
  if (b.error) parts.push(h('div', { class: 'empty' }, h('strong', { text: "Couldn't reach Modrinth" }), b.error));
  else if (!b.hits.length && b.loading) parts.push(h('div', { class: 'empty', text: 'Searching…' }));
  else if (!b.hits.length) parts.push(h('div', { class: 'empty' }, h('strong', { text: 'No matches' }), shaders ? `No shader packs for Minecraft ${inst.mcVersion} match that.` : `Nothing fits Minecraft ${inst.mcVersion} on ${loaderName(inst.loader)}.`));
  else {
    parts.push(h('div', { class: 'result-count', text: shaders
      ? `${b.total.toLocaleString()} shader packs for ${inst.mcVersion}${state.shaders.iris ? '' : ' · Iris is added when you install one'}`
      : `${b.total.toLocaleString()} mods for ${inst.mcVersion} ${loaderName(inst.loader)}` }));
    parts.push(h('ul', { class: 'list' }, ...b.hits.map((m) => resultCard(inst, m, installed.get(m.projectId)))));
    if (b.hits.length < b.total) {
      parts.push(h('div', { class: 'load-more' },
        h('button', { class: 'btn', disabled: b.loading, onclick: () => runSearch({ append: true }), text: b.loading ? 'Loading…' : 'Show more' })));
    }
  }
  box.replaceChildren(...parts);
}

function resultCard(inst, m, have) {
  const actions = h('div', { class: 'actions' });
  const shader = state.browse.type === 'shader';
  const findRec = () => (shader ? state.shaders.packs : state.mods).find((x) => x.projectId === m.projectId);

  function showInstalled(rec) {
    actions.replaceChildren(
      h('span', { class: 'installed' }, icon('check', 14), rec && rec.version ? `Installed ${rec.version}` : 'Installed'),
      versionsLink());
  }
  function versionsLink() {
    return h('button', { class: 'link', text: 'Choose version', onclick: () => showVersions() });
  }
  async function install(versionId) {
    actions.replaceChildren(h('button', { class: 'btn', disabled: true, text: 'Installing…' }));
    if (shader) {
      const had = state.shaders.enabled;
      const r = await attempt(() => sky.shaders.add(inst.id, m.slug, versionId || null, { title: m.title, icon: m.icon }));
      if (!r) return reset();
      await refreshShaders();
      toast(!had && state.shaders.enabled ? `Added ${m.title}. It's switched on for this profile.` : `Added ${m.title}. Pick it under Shader packs on the Mods tab.`);
      return showInstalled(findRec());
    }
    const added = await attempt(() => sky.mods.addModrinth(inst.id, m.slug, versionId || null));
    if (!added) return reset();
    await refreshMods();
    toast(added.length > 1 ? `Installed ${m.title} and ${added.length - 1} required mod${added.length > 2 ? 's' : ''}` : `Installed ${m.title}`);
    showInstalled(findRec());
  }
  async function showVersions() {
    actions.replaceChildren(h('span', { class: 'installed', text: 'Loading versions…' }));
    const builds = await attempt(() => sky.mods.builds(inst.id, m.projectId, shader ? 'shader' : 'mod'));
    if (!builds || !builds.length) return reset();
    const select = h('select', { class: 'input', 'aria-label': `${m.title} version` },
      ...builds.map((v) => h('option', { value: v.id, text: `${v.number}${v.type === 'release' ? '' : ` (${v.type})`}` })));
    actions.replaceChildren(select,
      h('div', { class: 'inline' },
        h('button', { class: 'btn quiet', text: 'Cancel', onclick: reset }),
        h('button', { class: 'btn accent', text: 'Install', onclick: () => install(select.value) })));
    select.focus();
  }
  function reset() {
    const rec = findRec();
    if (rec) return showInstalled(rec);
    actions.replaceChildren(h('button', { class: 'btn accent', text: 'Install', onclick: () => install() }), versionsLink());
  }
  reset();

  return h('li', { class: 'card' },
    m.icon ? h('img', { class: 'icon', src: m.icon, alt: '' }) : h('span', { class: 'icon' }),
    h('div', {},
      h('div', {}, h('button', { class: 'link t', style: 'color:var(--text)', onclick: () => sky.app.openExternal(`https://modrinth.com/${shader ? 'shader' : 'mod'}/${m.slug}`), text: m.title }),
        h('span', { class: 'by', text: `by ${m.author}` })),
      h('p', { text: m.description }),
      h('div', { class: 'stats' },
        h('span', { text: `${fmtCount(m.downloads)} downloads` }),
        m.updated && h('span', { text: `Updated ${fmtAgo(m.updated)}` }))),
    actions);
}

// ---------- Settings tab
async function renderSettings(panel, inst) {
  const preset = presetOf(inst);
  const field = (label, control, hint) =>
    h('div', { class: 'field' }, h('span', { class: 'label', text: label }), control, hint && h('span', { class: 'hint', text: hint }));
  const save = (patch) => attempt(async () => {
    await sky.instances.update(inst.id, patch);
    state.instances = await sky.instances.list();
    renderProfiles();
    renderHeader();
  });

  const name = h('input', { class: 'input', value: inst.name, maxlength: 40, onchange: (e) => e.target.value.trim() && save({ name: e.target.value.trim() }) });

  let version;
  if (inst.loader === 'fabric') {
    version = h('select', { class: 'input', disabled: true }, h('option', { text: inst.mcVersion }));
    sky.presets.versions(inst.presetId).then((vs) => {
      version.replaceChildren(...vs.map((v) => h('option', { value: v, selected: v === inst.mcVersion, text: v })));
      version.disabled = false;
    }).catch(() => toast("Couldn't reach Modrinth to list versions.", true));
    version.addEventListener('change', async (e) => {
      const v = e.target.value;
      const r = await attempt(() => sky.instances.changeVersion(inst.id, v), { busyLabel: `Switching to ${v}` });
      if (r) toast(r.skipped.length ? `On ${v}. No ${v} build for: ${r.skipped.join(', ')}` : `Switched to Minecraft ${v}`, r.skipped.length > 0);
      state.browse.forId = null;
      await refreshInstances();
    });
  } else {
    version = h('input', { class: 'input', value: '1.8.9 with Forge 11.15.1.2318', disabled: true });
  }

  const memOut = h('output', { text: `${(inst.memoryMb / 1024).toFixed(1)} GB` });
  const mem = h('input', {
    type: 'range', min: 1024, max: 16384, step: 512, value: inst.memoryMb, 'aria-label': 'Memory',
    oninput: (e) => (memOut.textContent = `${(e.target.value / 1024).toFixed(1)} GB`),
    onchange: (e) => save({ memoryMb: Number(e.target.value) }),
  });

  const java = h('input', { class: 'input', value: inst.javaPath || '', placeholder: 'Automatic', onchange: (e) => save({ javaPath: e.target.value.trim() }) });
  const s = state.settings;

  panel.replaceChildren(h('div', { class: 'form' },
    field('Name', name),
    field('Minecraft version', version,
      inst.loader === 'fabric' ? `Versions every ${preset ? preset.name : 'preset'} mod supports. Changing it re-downloads mods.` : null),
    field('Memory', h('div', { class: 'range' }, mem, memOut), '4 GB suits most SkyBlock setups.'),
    h('label', { class: 'check' },
      h('input', { type: 'checkbox', checked: inst.autoJoin, onchange: (e) => save({ autoJoin: e.target.checked }) }),
      h('span', {}, 'Join Hypixel on start', h('small', { text: 'Skips the title screen and connects to mc.hypixel.net.' }))),
    field('Java', h('div', { class: 'inline' }, java,
      h('button', { class: 'btn', text: 'Browse', onclick: async () => {
        const p = await sky.settings.pickJava();
        if (p) { java.value = p; save({ javaPath: p }); }
      } })), 'Leave on Automatic to use the Java SkyLaunch downloads.'),
    field('Java arguments', h('input', { class: 'input', value: inst.jvmArgs || '', placeholder: 'None', onchange: (e) => save({ jvmArgs: e.target.value }) })),
    h('hr', { class: 'divider' }),
    field('Theme', themePicker(), 'Applies to the launcher and the in-game menu. You can pick a different one in game under Appearance.'),
    h('label', { class: 'check' },
      h('input', { type: 'checkbox', checked: s.closeOnLaunch, onchange: async (e) => { state.settings = await sky.settings.set({ closeOnLaunch: e.target.checked }); } }),
      h('span', {}, 'Minimise SkyLaunch while playing', h('small', { text: 'Applies to every profile.' }))),
    h('div', { class: 'toolbar' },
      h('button', { class: 'btn', onclick: () => sky.instances.openFolder(inst.id), text: 'Open profile folder' }),
      h('span', { class: 'spacer' }),
      h('button', {
        class: 'btn danger', text: 'Delete profile',
        onclick: async () => { if (await attempt(() => sky.instances.remove(inst.id))) { toast('Profile deleted'); await refreshInstances(); } },
      }))));
}

// ---------- Logs tab
function logLine(line) {
  const cls = /\[SkyLaunch\]/.test(line) ? 'own' : /ERROR|Exception|FATAL/.test(line) ? 'err' : '';
  return h('div', { class: cls, text: line });
}
function renderLogs(panel) {
  const view = h('div', { class: 'logs', id: 'logView' }, ...state.logs.map(logLine));
  panel.replaceChildren(
    h('div', { class: 'toolbar' },
      h('button', { class: 'btn', text: 'Copy', disabled: !state.logs.length, onclick: async () => { await navigator.clipboard.writeText(state.logs.join('\n')); toast('Copied'); } }),
      h('button', { class: 'btn quiet', text: 'Clear', onclick: () => { state.logs = []; renderPanel(); } }),
      h('span', { class: 'spacer' }),
      current() && h('button', { class: 'btn quiet', text: 'Open logs folder', onclick: () => sky.instances.openFolder(current().id, 'logs') })),
    state.logs.length ? view : h('div', { class: 'empty' }, h('strong', { text: 'No output yet' }), 'Game output shows up here after you press Play.'));
  view.scrollTop = view.scrollHeight;
}

// ---------- New profile dialog
function openNewProfile() {
  let chosen = state.presets[0];
  let version = '';
  const name = h('input', { class: 'input', placeholder: chosen.name, maxlength: 40 });
  const versionSelect = h('select', { class: 'input', onchange: (e) => (version = e.target.value) });
  const versionField = h('div', { class: 'field' }, h('span', { class: 'label', text: 'Minecraft version' }), versionSelect);

  async function loadVersions() {
    version = '';
    versionField.hidden = chosen.era === 'legacy';
    versionSelect.replaceChildren(h('option', { value: '', text: 'Newest supported' }));
    if (chosen.era === 'legacy') return;
    versionSelect.disabled = true;
    try {
      const vs = await sky.presets.versions(chosen.id);
      versionSelect.replaceChildren(
        h('option', { value: '', text: vs[0] ? `Newest supported (${vs[0]})` : 'Newest supported' }),
        ...vs.map((v) => h('option', { value: v, text: v })));
    } catch { /* keep default */ }
    versionSelect.disabled = false;
  }

  const option = (p) => h('label', { class: 'preset' },
    h('input', {
      type: 'radio', name: 'preset', checked: p === chosen,
      onchange: () => { chosen = p; name.placeholder = p.name; loadVersions(); },
    }),
    h('div', {}, h('strong', { text: p.name }), h('span', { text: p.summary })));
  const group = (title, era) => [
    h('div', { class: 'preset-group', text: title }),
    h('div', { class: 'presets', role: 'radiogroup', 'aria-label': title }, ...state.presets.filter((p) => p.era === era).map(option)),
  ];

  const create = h('button', {
    class: 'btn accent', text: 'Create',
    onclick: async () => {
      create.disabled = true;
      const inst = await attempt(() => sky.instances.create({ name: name.value.trim() || chosen.name, presetId: chosen.id, mcVersion: version || undefined }));
      if (!inst) { create.disabled = false; return; }
      $('dialog').close();
      state.tab = 'mods';
      await refreshInstances(inst.id);
      await selectTab('mods');
      const r = await attempt(() => sky.instances.installPreset(inst.id), { busyLabel: 'Installing mods' });
      if (r) toast(`${inst.name} is ready`);
      await refreshInstances();
    },
  });

  const d = $('dialog');
  d.replaceChildren(
    h('div', { class: 'dlg-body' },
      h('h2', { text: 'New profile' }),
      h('p', { text: 'Pick a starting point. You can change mods any time.' }),
      ...group('Modern Minecraft, Fabric', 'modern'),
      ...group('Minecraft 1.8.9, Forge', 'legacy'),
      h('div', { class: 'form' }, h('div', { class: 'field' }, h('span', { class: 'label', text: 'Name' }), name), versionField)),
    h('div', { class: 'dlg-foot' }, h('button', { class: 'btn', text: 'Cancel', onclick: () => d.close() }), create));
  d.showModal();
  loadVersions();
}
$('newProfile').addEventListener('click', openNewProfile);
$('newProfile').prepend(icon('plus', 16));
$('joinFriend').prepend(icon('users', 16));
[['mods', 'box'], ['browse', 'compass'], ['settings', 'sliders'], ['logs', 'terminal']].forEach(([tab, ic]) => {
  const b = document.querySelector(`#tabs [data-tab=${tab}]`);
  if (b) b.prepend(icon(ic, 15));
});

// ---------- Join a friend (shared worlds)
function openJoinFriend() {
  const d = $('dialog');
  let plan = null;
  let timer;
  const details = h('div', { class: 'join-details' });
  const join = h('button', { class: 'btn accent', text: 'Install and join', disabled: true });
  const code = h('textarea', {
    class: 'input invite-box', rows: 3, spellcheck: false, autofocus: true,
    placeholder: 'Paste the invite your friend copied from Share world (starts with skylaunch:)',
    oninput: () => { clearTimeout(timer); timer = setTimeout(check, 250); },
  });

  async function check() {
    const text = code.value.trim();
    plan = null;
    join.disabled = true;
    if (!text) { details.replaceChildren(); return; }
    try {
      plan = await sky.share.plan(text);
    } catch (e) {
      details.replaceChildren(h('p', { class: 'join-error', text: e.message }));
      return;
    }
    const need = plan.mods.filter((m) => !m.installed);
    join.disabled = false;
    join.textContent = need.length ? `Install ${need.length} mod${need.length === 1 ? '' : 's'} and join` : 'Join';
    const parts = [
      h('p', { class: 'join-summary' },
        h('strong', { text: `${plan.host}'s world` }),
        ` · Minecraft ${plan.mcVersion} ${plan.loader === 'fabric' ? 'Fabric' : plan.loader}`),
      h('p', { class: 'note join-note', text: plan.profileExists
        ? `Uses your "${plan.profileName}" profile. Mods ${plan.host} doesn't have are removed from it so yours match.`
        : `Creates a profile called "${plan.profileName}" with exactly ${plan.host}'s mods, so your normal profiles stay untouched.` }),
    ];
    if (plan.mods.length) {
      parts.push(h('div', { class: 'section-title', text: `Mods (${plan.mods.length})` }),
        h('ul', { class: 'join-mods' }, ...plan.mods.map((m) => h('li', {},
          h('span', { text: m.title }),
          h('span', { class: m.installed ? 'ok' : 'todo', text: m.installed ? 'Installed' : 'Will install' })))));
    } else {
      parts.push(h('p', { class: 'note', text: 'No Modrinth mods needed.' }));
    }
    if (plan.files.length) {
      parts.push(h('div', { class: 'section-title', text: `Ask ${plan.host} for these files (${plan.files.length})` }),
        h('p', { class: 'note', text: "They aren't on Modrinth, so they can't be installed for you. Drop them onto the profile's Mods tab before joining if the world needs them." }),
        h('ul', { class: 'join-mods' }, ...plan.files.map((f) => h('li', {}, h('span', { text: f }), h('span', { class: 'todo', text: 'Manual' })))));
    }
    details.replaceChildren(...parts);
  }

  join.addEventListener('click', async () => {
    if (!plan) return;
    if (!state.account.active) { toast('Sign in with Microsoft first.', true); return; }
    join.disabled = true;
    join.textContent = 'Installing…';
    d.close();
    const r = await attempt(() => sky.share.join(code.value.trim()), { busyLabel: `Getting ${plan.host}'s mods` });
    if (r && r.failed.length) toast(`Couldn't install: ${r.failed.join(', ')}. You may not be able to join.`, true);
  });

  d.replaceChildren(
    h('div', { class: 'dlg-body' },
      h('h2', { text: 'Join a friend' }),
      h('p', { text: "Paste the invite from your friend's Share world panel. SkyLaunch installs the same mods they have, then joins their world." }),
      code,
      details),
    h('div', { class: 'dlg-foot' }, h('button', { class: 'btn', text: 'Cancel', onclick: () => d.close() }), join));
  d.showModal();
  // Pre-fill from the clipboard when it already holds an invite.
  navigator.clipboard.readText().then((t) => {
    if (t && t.trim().startsWith('skylaunch:') && !code.value) { code.value = t.trim(); check(); }
  }).catch(() => {});
}
$('joinFriend').addEventListener('click', openJoinFriend);

sky.onProfilesChanged(async ({ select }) => {
  state.tab = 'mods';
  await refreshInstances(select);
});
sky.onLaunchError((msg) => {
  state.game = { id: null, state: 'stopped' };
  state.busy = null;
  renderHeader();
  renderProfiles();
  toast(msg, true);
});

// ---------- events from the main process
sky.onProgress(({ label, fraction }) => {
  state.busy = state.game.state === 'running' ? null : { label, fraction };
  renderHeader();
});

let logFlush;
sky.onLog((line) => {
  state.logs.push(line);
  if (state.logs.length > 5000) state.logs.splice(0, state.logs.length - 5000);
  if (state.tab !== 'logs' || logFlush) return;
  logFlush = requestAnimationFrame(() => {
    logFlush = null;
    const view = $('logView');
    if (!view || view.childElementCount > state.logs.length) return renderPanel();
    const stick = view.scrollTop + view.clientHeight >= view.scrollHeight - 30;
    view.append(...state.logs.slice(view.childElementCount).map(logLine));
    if (stick) view.scrollTop = view.scrollHeight;
  });
});

sky.onGameState(async ({ id, state: st, code }) => {
  state.game = { id: st === 'stopped' ? null : id, state: st };
  if (st !== 'preparing') state.busy = null;
  renderHeader();
  renderProfiles();
  renderUpdate();
  if (st === 'stopped' && code && code !== 0) toast(`Minecraft closed with an error (code ${code}). See Logs.`, true);
  if (st === 'stopped') await refreshInstances();
});

// ---------- themes (the in-game menu uses the same four; see Theme.java)
const THEMES = [
  { id: 'midnight', name: 'Midnight', note: 'Dark' },
  { id: 'dawn', name: 'Dawn', note: 'Orange and yellow' },
  { id: 'violet', name: 'Violet', note: 'Purple' },
  { id: 'light', name: 'Light', note: 'White and blue' },
];
function applyTheme(id, fade) {
  const root = document.documentElement;
  if (fade) {
    root.classList.add('theme-fade');
    clearTimeout(applyTheme.t);
    applyTheme.t = setTimeout(() => root.classList.remove('theme-fade'), 320);
  }
  root.dataset.theme = THEMES.some((t) => t.id === id) ? id : 'midnight';
}

function themePicker() {
  const current = document.documentElement.dataset.theme || 'midnight';
  const pick = async (id) => {
    applyTheme(id, true);
    state.settings = await sky.settings.set({ theme: id });
    picker.querySelectorAll('.theme-card').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.id === id)));
  };
  const picker = h('div', { class: 'theme-picker', role: 'radiogroup', 'aria-label': 'Theme' },
    ...THEMES.map((t) => h('button', {
      class: 'theme-card', role: 'radio', 'data-id': t.id, 'aria-checked': String(t.id === current),
      onclick: () => pick(t.id),
    },
    // A tiny picture of the launcher in that theme: sidebar, two cards and a Play button.
    h('span', { class: `theme-mini t-${t.id}` },
      h('i', { class: 'm-side' }), h('i', { class: 'm-card' }), h('i', { class: 'm-card short' }), h('i', { class: 'm-play' })),
    h('span', { class: 'theme-name' }, h('strong', { text: t.name }), h('small', { text: t.note })))));
  return picker;
}

// ---------- launcher updates
let updateState = { status: 'idle' };
let checkedByHand = false;

function renderUpdate() {
  const box = $('update');
  const u = updateState;
  const parts = [];
  if (u.status === 'ready') {
    const running = state.game.state !== 'stopped';
    parts.push(h('div', { class: 'update-card' },
      h('strong', { text: `Update ${u.version} is ready` }),
      h('span', { text: running ? 'Close Minecraft to restart, or it installs next time you open SkyLaunch.' : 'Restart now, or it installs next time you open SkyLaunch.' }),
      h('button', {
        class: 'btn accent', disabled: running, text: 'Restart to update',
        onclick: () => attempt(() => sky.update.install()),
      })));
  } else if (u.status === 'downloading') {
    parts.push(h('div', { class: 'update-card' },
      h('strong', { text: `Downloading update ${u.version || ''}`.trim() }),
      h('div', { class: 'bar' }, h('div', { class: 'fill', style: `width:${Math.round((u.progress || 0) * 100)}%` }))));
  }
  let line;
  if (u.status === 'dev') line = `Version ${u.current || ''} (dev)`;
  else if (u.status === 'checking') line = 'Checking for updates…';
  else if (u.status === 'latest') line = `Version ${u.current} · Up to date`;
  else if (u.status === 'error') line = checkedByHand ? u.error : `Version ${u.current || ''}`;
  else line = `Version ${u.current || ''}`;
  const canCheck = !['checking', 'downloading', 'ready', 'dev'].includes(u.status);
  parts.push(h('div', { class: 'update-line' },
    h('span', { text: line, title: u.error || '' }),
    canCheck && h('button', {
      class: 'link', text: 'Check for updates',
      onclick: async () => { checkedByHand = true; await attempt(() => sky.update.check()); },
    })));
  box.replaceChildren(...parts);
}

sky.onUpdate((st) => {
  const wasReady = updateState.status === 'ready';
  updateState = st;
  renderUpdate();
  if (st.status === 'ready' && !wasReady) toast(`SkyLaunch ${st.version} downloaded`);
  if (st.status === 'latest' && checkedByHand) { toast("You're on the latest version"); checkedByHand = false; }
});

// ---------- boot
(async function boot() {
  try {
    const [presets, account, settings, running] = await Promise.all([
      sky.presets.list(), sky.auth.get(), sky.settings.get(), sky.game.running(),
    ]);
    Object.assign(state, { presets, account, settings });
    try {
      const look = await sky.app.look();
      if (look.mica) document.documentElement.classList.add('mica');
      applyTheme(look.theme || settings.theme);
    } catch { applyTheme(settings.theme); }
    if (running) state.game = { id: running, state: 'running' };
    renderAccount();
    try { updateState = await sky.update.get(); } catch { /* older main process */ }
    renderUpdate();
    await refreshInstances();
  } catch (e) {
    toast(e.message, true);
  }
})();
