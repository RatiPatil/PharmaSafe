import { writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
let commit = process.env.RENDER_GIT_COMMIT || process.env.GITHUB_SHA || '';
if (!commit) { try { commit = execFileSync('git', ['rev-parse','HEAD'], {encoding:'utf8'}).trim(); } catch { commit = 'local'; } }
writeFileSync(new URL('../dist/release.json',import.meta.url), JSON.stringify({commit}));
