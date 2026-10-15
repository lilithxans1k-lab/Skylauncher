// Modrinth API client: version lookup, dependency resolution and search.
// API docs: https://docs.modrinth.com/api/
const { getJson } = require('./net');

const API = 'https://api.modrinth.com/v2';
const enc = (v) => encodeURIComponent(JSON.stringify(v));

// "1.21.5" -> [1,21,5]; "26.1" -> [26,1,0]. Non-release strings return null.
function parseRelease(v) {
  if (!/^\d+\.\d+(\.\d+)?$/.test(v)) return null;
  const [a, b, c = 0] = v.split('.').map(Number);
  return [a, b, Number(c)];
}

function compareVersions(x, y) {
  const a = parseRelease(x);
  const b = parseRelease(y);
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] - b[i];
  return 0;
}

const sortNewestFirst = (list) => [...list].filter(parseRelease).sort((a, b) => compareVersions(b, a));

async function projectVersions(idOrSlug, loader, gameVersion) {
  let url = `${API}/project/${encodeURIComponent(idOrSlug)}/version?loaders=${enc([loader])}`;
  if (gameVersion) url += `&game_versions=${enc([gameVersion])}`;
  try {
    return await getJson(url);
  } catch (e) {
    if (e.status === 404) return [];
    throw e;
  }
}

/** Release game versions (newest first) that a single project has builds for on this loader. */
async function supportedGameVersions(slug, loader) {
  const versions = await projectVersions(slug, loader);
  const set = new Set();
  for (const v of versions) for (const g of v.game_versions) set.add(g);
  return sortNewestFirst([...set]);
}

/** Newest release version every slug in the list supports. Null if they share none. */
async function newestCommonVersion(slugs, loader) {
  if (!slugs.length) return null;
  const lists = await Promise.all(slugs.map((s) => supportedGameVersions(s, loader)));
  const [first, ...rest] = lists;
  const common = first.filter((v) => rest.every((l) => l.includes(v)));
  return common[0] || null;
}

/** All versions any of the given slugs supports, for a version picker. */
async function versionsForPicker(slugs, loader) {
  const lists = await Promise.all(slugs.map((s) => supportedGameVersions(s, loader)));
  if (!lists.length) return [];
  const [first, ...rest] = lists;
  return first.filter((v) => rest.every((l) => l.includes(v)));
}

function pickBuild(versions) {
  return versions.find((v) => v.version_type === 'release') || versions.find((v) => v.version_type === 'beta') || versions[0] || null;
}

/**
 * Resolve a list of mods (plus their required dependencies) to concrete files for one
 * game version + loader. Returns { mods: [...], missing: [slug...] }.
 * Each mod: { projectId, versionId, slug, title, versionNumber, filename, url, sha1, size }.
 */
async function resolveMods({ required = [], optional = [] }, loader, gameVersion) {
  const out = new Map(); // projectId -> mod
  const missing = [];
  // A required entry can be a slug/id, or { versionId } to pin an exact build.
  const queue = [
    ...required.map((s) => (typeof s === 'string' ? { ref: s, must: true } : { ref: s.versionId, versionId: s.versionId, must: true })),
    ...optional.map((s) => ({ ref: s, must: false })),
  ];
  const seen = new Set();

  while (queue.length) {
    const { ref, must, versionId } = queue.shift();
    if (seen.has(ref)) continue;
    seen.add(ref);

    let build;
    if (versionId) {
      try { build = await getJson(`${API}/version/${versionId}`); } catch { build = null; }
    } else {
      build = pickBuild(await projectVersions(ref, loader, gameVersion));
    }
    if (!build) {
      if (must) missing.push(ref);
      continue;
    }
    if (out.has(build.project_id)) continue;

    const file = build.files.find((f) => f.primary) || build.files[0];
    out.set(build.project_id, {
      projectId: build.project_id,
      versionId: build.id,
      slug: ref,
      title: ref,
      versionNumber: build.version_number,
      filename: file.filename,
      url: file.url,
      sha1: file.hashes && file.hashes.sha1,
      size: file.size,
    });

    for (const dep of build.dependencies || []) {
      if (dep.dependency_type !== 'required') continue;
      if (dep.project_id && out.has(dep.project_id)) continue;
      queue.push({ ref: dep.project_id || dep.version_id, versionId: dep.project_id ? undefined : dep.version_id, must: must });
    }
  }

  // Fill in human-readable titles and slugs in one request.
  const ids = [...out.keys()];
  if (ids.length) {
    try {
      const projects = await getJson(`${API}/projects?ids=${enc(ids)}`);
      for (const p of projects) {
        const m = out.get(p.id);
        if (m) { m.title = p.title; m.slug = p.slug; m.icon = p.icon_url || null; }
      }
    } catch { /* titles are cosmetic */ }
  }
  return { mods: [...out.values()], missing };
}

/**
 * Search mods compatible with a profile.
 * sort: relevance | downloads | follows | newest | updated
 * category: a Modrinth category such as "optimization", or "" for all.
 */
async function search(query, loader, gameVersion, { sort = 'relevance', category = '', offset = 0, limit = 20, type = 'mod' } = {}) {
  // Shader packs run through Iris on Fabric, so filter on Iris support instead of the mod loader.
  const facets = type === 'shader'
    ? [['categories:iris'], [`versions:${gameVersion}`], ['project_type:shader']]
    : [[`categories:${loader}`], [`versions:${gameVersion}`], ['project_type:mod']];
  if (category) facets.push([`categories:${category}`]);
  const url = `${API}/search?query=${encodeURIComponent(query)}&limit=${limit}&offset=${offset}&index=${encodeURIComponent(sort)}&facets=${enc(facets)}`;
  const res = await getJson(url);
  return {
    total: res.total_hits,
    hits: res.hits.map((h) => ({
      slug: h.slug,
      projectId: h.project_id,
      title: h.title,
      description: h.description,
      author: h.author,
      downloads: h.downloads,
      updated: h.date_modified,
      categories: h.display_categories || h.categories || [],
      icon: h.icon_url || null,
    })),
  };
}

/** Builds of one mod that fit a profile, newest first. */
async function compatibleBuilds(idOrSlug, loader, gameVersion) {
  const list = await projectVersions(idOrSlug, loader, gameVersion);
  return list.map((v) => ({
    id: v.id,
    name: v.name,
    number: v.version_number,
    type: v.version_type,
    published: v.date_published,
  }));
}

module.exports = {
  resolveMods, search, compatibleBuilds, projectVersions, pickBuild, newestCommonVersion, versionsForPicker, supportedGameVersions,
  compareVersions, parseRelease, sortNewestFirst,
};
