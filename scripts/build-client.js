// Builds the SkyLaunch in-game client for both versions and copies the jars to
// resources/client, where the launcher picks them up.
//
//   npm run build:client            build both
//   npm run build:client -- fabric  only 26.2 Fabric
//   npm run build:client -- forge   only 1.8.9 Forge
//
// The two builds need different Java versions to run Gradle:
//   Fabric 26.2  -> JDK 25        (set SKYLAUNCH_JDK25 to its folder, or JAVA_HOME)
//   Forge 1.8.9  -> JDK 17 or 21  (set SKYLAUNCH_JDK17 to its folder, or JAVA_HOME)
// Forge also downloads Java 8 for compiling by itself.
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const out = path.join(root, 'resources', 'client');
const win = process.platform === 'win32';

const targets = {
  fabric: { dir: 'client-mod/fabric', jdkEnv: 'SKYLAUNCH_JDK25', jarPrefix: 'skylaunch-client-fabric-', name: 'skylaunch-client-fabric.jar' },
  forge: { dir: 'client-mod/forge189', jdkEnv: 'SKYLAUNCH_JDK17', jarPrefix: 'skylaunch-client-forge-1.8.9-', name: 'skylaunch-client-forge-1.8.9.jar' },
};

const wanted = process.argv.slice(2).filter((a) => targets[a]);
const list = wanted.length ? wanted : Object.keys(targets);
fs.mkdirSync(out, { recursive: true });

let failed = false;
for (const key of list) {
  const t = targets[key];
  const dir = path.join(root, t.dir);
  const env = { ...process.env };
  if (process.env[t.jdkEnv]) env.JAVA_HOME = process.env[t.jdkEnv];
  console.log(`\n== Building ${key} (JAVA_HOME=${env.JAVA_HOME || 'system default'})`);
  const r = spawnSync(win ? 'gradlew.bat' : './gradlew', ['build', '--no-daemon'], { cwd: dir, env, stdio: 'inherit', shell: win });
  if (r.status !== 0) {
    console.error(`!! ${key} build failed. Copy the first error above and send it over.`);
    failed = true;
    continue;
  }
  const libs = path.join(dir, 'build', 'libs');
  const jar = fs.readdirSync(libs).find((f) => f.startsWith(t.jarPrefix) && f.endsWith('.jar') && !/-(sources|dev|all)\.jar$/.test(f));
  if (!jar) {
    console.error(`!! ${key}: no jar found in ${libs}`);
    failed = true;
    continue;
  }
  fs.copyFileSync(path.join(libs, jar), path.join(out, t.name));
  console.log(`== ${key}: copied ${jar} -> resources/client/${t.name}`);
}
process.exit(failed ? 1 : 0);
