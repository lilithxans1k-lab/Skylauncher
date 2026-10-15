// Compiles the shared client code as Java 8 and runs its tests. Needs any JDK 8+ on PATH.
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const core = path.join(__dirname, '..', 'client-mod', 'core', 'src');
const outDir = fs.mkdtempSync(path.join(os.tmpdir(), 'skylaunch-core-'));
const files = [];
(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p);
    else if (p.endsWith('.java')) files.push(p);
  }
})(core);
execFileSync('javac', ['--release', '8', '-Xlint:-options', '-d', outDir, ...files], { stdio: 'inherit' });
execFileSync('java', ['-cp', outDir, 'dev.skylaunch.client.CoreTest'], { stdio: 'inherit' });
fs.rmSync(outDir, { recursive: true, force: true });
