// Microsoft account sign-in through msmc. The launcher never sees the player's password:
// Microsoft's own sign-in page opens in a separate window and hands back a token.
// Refresh tokens are encrypted with Windows DPAPI via Electron's safeStorage.
const { safeStorage } = require('electron');
const { Auth, lexicon } = require('msmc');
const paths = require('./paths');
const store = require('./store');

function friendlyError(e) {
  if (typeof e === 'string') {
    if (e === 'error.gui.closed') return 'Sign-in window was closed before finishing.';
    if (e.includes('error.auth.minecraft.profile')) return "This Microsoft account doesn't own Minecraft: Java Edition.";
    try { return lexicon.getCode(e); } catch { return e; }
  }
  if (e && e.ts) {
    if (String(e.ts).includes('profile')) return "This Microsoft account doesn't own Minecraft: Java Edition.";
    try { return lexicon.getCode(e.ts); } catch { /* fall through */ }
  }
  return (e && e.message) || 'Sign-in failed.';
}

function load() {
  return store.read(paths.accountsFile(), { active: null, accounts: [] });
}

function persist(data) {
  store.write(paths.accountsFile(), data);
}

function encrypt(token) {
  if (!safeStorage.isEncryptionAvailable()) return { plain: token };
  return { enc: safeStorage.encryptString(token).toString('base64') };
}

function decrypt(saved) {
  if (saved.plain) return saved.plain;
  return safeStorage.decryptString(Buffer.from(saved.enc, 'base64'));
}

function publicView() {
  const { active, accounts } = load();
  return {
    active,
    accounts: accounts.map(({ uuid, name }) => ({ uuid, name })),
  };
}

function remember(xbox, mc) {
  const data = load();
  const entry = { uuid: mc.profile.id, name: mc.profile.name, refresh: encrypt(xbox.save()) };
  data.accounts = [...data.accounts.filter((a) => a.uuid !== entry.uuid), entry];
  data.active = entry.uuid;
  persist(data);
}

/** Opens Microsoft's sign-in window. Must be called from the Electron main process. */
async function signIn() {
  try {
    const auth = new Auth('select_account');
    const xbox = await auth.launch('electron', { width: 520, height: 680, resizable: false, autoHideMenuBar: true });
    const mc = await xbox.getMinecraft();
    remember(xbox, mc);
    return publicView();
  } catch (e) {
    throw new Error(friendlyError(e));
  }
}

function switchTo(uuid) {
  const data = load();
  if (data.accounts.some((a) => a.uuid === uuid)) data.active = uuid;
  persist(data);
  return publicView();
}

function signOut(uuid) {
  const data = load();
  data.accounts = data.accounts.filter((a) => a.uuid !== uuid);
  if (data.active === uuid) data.active = data.accounts[0] ? data.accounts[0].uuid : null;
  persist(data);
  return publicView();
}

/** Fresh launch credentials for the active account, refreshing tokens silently. */
async function launchAuth() {
  const data = load();
  const acct = data.accounts.find((a) => a.uuid === data.active);
  if (!acct) throw new Error('Sign in with your Microsoft account first.');
  try {
    const auth = new Auth('select_account');
    const xbox = await auth.refresh(decrypt(acct.refresh));
    const mc = await xbox.getMinecraft();
    remember(xbox, mc); // Microsoft rotates refresh tokens; keep the newest.
    return mc.mclc();
  } catch (e) {
    throw new Error(`Your sign-in for ${acct.name} expired. Sign in again. (${friendlyError(e)})`);
  }
}

module.exports = { signIn, signOut, switchTo, publicView, launchAuth };
