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
  browse: { query: '', sort: 'relevance', category: '', skyblock: false, hits: [], total: 0, loading: false, error: null, forId: null },
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

async function refreshMods() {
  const inst = current();
  state.mods = inst ? await sky.mods.list(inst.id) : [];
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
      h('span', { class: 'name', text: inst.name }),
      h('span', { class: `sub${live ? ' live' : ''}`, text: live ? 'Playing' : `${inst.mcVersion} ${loaderName(inst.loader)}` }));
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
  if (running) button = h('button', { class: 'btn big', onclick: () => sky.game.stop(), text: 'Close game' });
  else if (!state.account.active) button = h('button', { class: 'btn accent big', onclick: signIn, text: 'Sign in to play' });
  else button = h('button', { class: 'btn accent big', disabled: preparing || otherRunning || !!state.busy, onclick: play, text: 'Play' });

  let below;
  if (state.busy) {
    below = h('div', { class: 'progress' },
      h('div', { class: 'bar' }, h('div', { class: 'fill', style: `width:${Math.round(state.busy.fraction * 100)}%` })),
      h('span', { class: 'label', text: state.busy.label }));
  } else if (running) below = h('span', { class: 'hint-line', text: 'Running' });
  else if (otherRunning) below = h('span', { class: 'hint-line', text: 'Another profile is running' });
  else if (inst.autoJoin) below = h('span', { class: 'hint-line', text: 'Joins mc.hypixel.net' });

  const meta = [`Minecraft ${inst.mcVersion}`, loaderName(inst.loader), preset ? preset.name : null].filter(Boolean).join(', ');
  box.replaceChildren(
    h('div', { class: 'title' }, h('h1', { text: inst.name }), h('div', { class: 'meta', text: meta })),
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
  const preset = presetOf(inst);
  let clientStatus = 'not-built';
  try { clientStatus = await sky.mods.clientStatus(inst.id); } catch { /* shown as unavailable */ }
  const toolbar = h('div', { class: 'toolbar' },
    h('button', { class: 'btn accent', onclick: () => selectTab('browse'), text: 'Browse Modrinth' }),
    h('button', { class: 'btn', onclick: async () => {
      const added = await attempt(() => sky.mods.pickFiles(inst.id));
      if (added && added.length) { toast(`Added ${added.length} file${added.length === 1 ? '' : 's'}`); renderPanel(); }
    }, text: 'Add .jar files' }),
    h('button', { class: 'btn quiet', onclick: () => sky.instances.openFolder(inst.id, 'mods'), text: 'Open folder' }),
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
  panel.replaceChildren(toolbar, ...sections,
    h('p', { class: 'note' }, 'Only use mods Hypixel allows; macros and auto-clickers get accounts banned. ',
      h('button', { class: 'link', onclick: () => sky.app.openExternal('https://hypixel.net/allowed-modifications'), text: 'Allowed mods' })));
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
  const files = [...e.dataTransfer.files].map((f) => sky.pathForFile(f)).filter((p) => /\.jar$/i.test(p));
  if (!files.length) return toast('Only .jar files can be added.', true);
  const added = await attempt(() => sky.mods.addFiles(inst.id, files));
  if (added) { toast(`Added ${added.length} file${added.length === 1 ? '' : 's'}`); selectTab('mods'); }
});

// ---------- Browse tab
const CATEGORIES = [
  ['', 'All'], ['utility', 'Utility'], ['optimization', 'Performance'], ['decoration', 'Visual'],
  ['social', 'Social'], ['game-mechanics', 'Gameplay'], ['library', 'Libraries'],
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
  if (!append) { b.hits = []; b.forId = inst.id; }
  drawBrowseResults();
  try {
    const q = `${b.skyblock ? 'skyblock ' : ''}${b.query}`.trim();
    const res = await sky.mods.search(inst.id, q, { sort: b.sort, category: b.category, offset: append ? b.hits.length : 0 });
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
  const search = h('input', {
    class: 'input', type: 'search', value: b.query, placeholder: 'Search Modrinth',
    'aria-label': 'Search Modrinth',
    oninput: (e) => { b.query = e.target.value; clearTimeout(searchTimer); searchTimer = setTimeout(() => runSearch(), 300); },
  });
  const sort = h('select', { class: 'input', 'aria-label': 'Sort by', onchange: (e) => { b.sort = e.target.value; runSearch(); } },
    ...SORTS.map(([v, l]) => h('option', { value: v, selected: v === b.sort, text: l })));
  const chip = (label, pressed, onclick) => h('button', { class: 'chip', 'aria-pressed': String(pressed), onclick, text: label });
  const chips = h('div', { class: 'chips' },
    chip('SkyBlock', b.skyblock, () => { b.skyblock = !b.skyblock; renderPanel(); runSearch(); }),
    ...CATEGORIES.map(([v, l]) => chip(l, b.category === v, () => { b.category = v; renderPanel(); runSearch(); })));

  panel.replaceChildren(
    h('div', { class: 'search-bar' }, search, sort),
    chips,
    h('div', { id: 'browseResults' }));
  drawBrowseResults();
  if (b.forId !== inst.id || (!b.hits.length && !b.loading && !b.error)) runSearch();
  if (!b.query) search.focus();
}

function drawBrowseResults() {
  const box = $('browseResults');
  const inst = current();
  if (!box || !inst) return;
  const b = state.browse;
  const installed = new Map(state.mods.filter((m) => m.projectId).map((m) => [m.projectId, m]));

  const parts = [];
  if (b.error) parts.push(h('div', { class: 'empty' }, h('strong', { text: "Couldn't reach Modrinth" }), b.error));
  else if (!b.hits.length && b.loading) parts.push(h('div', { class: 'empty', text: 'Searching…' }));
  else if (!b.hits.length) parts.push(h('div', { class: 'empty' }, h('strong', { text: 'No matches' }), `Nothing fits Minecraft ${inst.mcVersion} on ${loaderName(inst.loader)}.`));
  else {
    parts.push(h('div', { class: 'result-count', text: `${b.total.toLocaleString()} mods for ${inst.mcVersion} ${loaderName(inst.loader)}` }));
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

  function showInstalled(rec) {
    actions.replaceChildren(
      h('span', { class: 'installed', text: rec && rec.version ? `Installed ${rec.version}` : 'Installed' }),
      versionsLink());
  }
  function versionsLink() {
    return h('button', { class: 'link', text: 'Choose version', onclick: () => showVersions() });
  }
  async function install(versionId) {
    actions.replaceChildren(h('button', { class: 'btn', disabled: true, text: 'Installing…' }));
    const added = await attempt(() => sky.mods.addModrinth(inst.id, m.slug, versionId || null));
    if (!added) return reset();
    await refreshMods();
    const rec = state.mods.find((x) => x.projectId === m.projectId);
    toast(added.length > 1 ? `Installed ${m.title} and ${added.length - 1} required mod${added.length > 2 ? 's' : ''}` : `Installed ${m.title}`);
    showInstalled(rec);
  }
  async function showVersions() {
    actions.replaceChildren(h('span', { class: 'installed', text: 'Loading versions…' }));
    const builds = await attempt(() => sky.mods.builds(inst.id, m.projectId));
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
    const rec = state.mods.find((x) => x.projectId === m.projectId);
    if (rec) return showInstalled(rec);
    actions.replaceChildren(h('button', { class: 'btn accent', text: 'Install', onclick: () => install() }), versionsLink());
  }
  reset();

  return h('li', { class: 'card' },
    m.icon ? h('img', { class: 'icon', src: m.icon, alt: '' }) : h('span', { class: 'icon' }),
    h('div', {},
      h('div', {}, h('button', { class: 'link t', style: 'color:var(--text)', onclick: () => sky.app.openExternal(`https://modrinth.com/mod/${m.slug}`), text: m.title }),
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
  if (st === 'stopped' && code && code !== 0) toast(`Minecraft closed with an error (code ${code}). See Logs.`, true);
  if (st === 'stopped') await refreshInstances();
});

// Use the Windows accent colour, with black or white text on it, whichever reads better.
function applyAccent(hex) {
  if (!hex) return;
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  const lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  const root = document.documentElement.style;
  root.setProperty('--accent', hex);
  root.setProperty('--on-accent', lum > 0.25 ? '#000000' : '#ffffff');
}
sky.onAccent(applyAccent);

// ---------- boot
(async function boot() {
  try {
    const [presets, account, settings, accent, running] = await Promise.all([
      sky.presets.list(), sky.auth.get(), sky.settings.get(), sky.app.accent(), sky.game.running(),
    ]);
    Object.assign(state, { presets, account, settings });
    applyAccent(accent);
    if (running) state.game = { id: running, state: 'running' };
    renderAccount();
    await refreshInstances();
  } catch (e) {
    toast(e.message, true);
  }
})();
