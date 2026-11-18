import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
// Attest the source actually checked out for this build. Hosting environment
// variables can be inherited from an earlier deployment or manually configured.
let commit = '';
try { commit = execFileSync('git', ['rev-parse','HEAD'], {encoding:'utf8',stdio:['ignore','pipe','ignore']}).trim(); } catch { /* Source archives may omit Git metadata. */ }
if (!/^[0-9a-f]{40}$/.test(commit)) commit = process.env.RENDER_GIT_COMMIT || process.env.GITHUB_SHA || 'local';
const html=readFileSync(new URL('../dist/index.html',import.meta.url),'utf8');
const initialAssets=[...new Set([...html.matchAll(/(?:src|href)="(\/assets\/[^"]+\.(?:js|css))"/g)].map(match=>match[1]))];
const entry=html.match(/<script[^>]+src="([^"]+)"/)?.[1];
if(!entry||!initialAssets.includes(entry))throw Error('Production HTML has no verifiable JavaScript entry.');
writeFileSync(new URL('../dist/release.json',import.meta.url), JSON.stringify({commit,entry,initialAssets}));
