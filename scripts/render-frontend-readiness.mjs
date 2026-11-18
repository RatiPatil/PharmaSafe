// A release marker alone can be fresh while a CDN still serves older HTML.
export async function frontendReady(base, release, fetcher = fetch) {
  const assets = release.initialAssets;
  if (!Array.isArray(assets) || !assets.length || assets.length > 20 ||
      !assets.every(path => typeof path === 'string' && /^\/assets\/[A-Za-z0-9_.-]+\.(?:js|css)$/.test(path)) ||
      typeof release.entry !== 'string' || !assets.includes(release.entry)) return false;
  try {
    const options = () => ({headers: {'Cache-Control': 'no-cache'}, signal: AbortSignal.timeout(15000)});
    const page = await fetcher(base.replace(/\/+$/, '') + '/', options());
    if (!page.ok) return false;
    const html = await page.text();
    if (!assets.every(path => html.includes(`"${path}"`))) return false;
    const checks = await Promise.allSettled(assets.map(async path => {
      const response = await fetcher(new URL(path, base).href, options());
      const type = response.headers.get('content-type') || '';
      const valid = response.ok && (path.endsWith('.css') ? /^text\/css\b/i.test(type) : /^(?:text|application)\/(?:javascript|ecmascript)\b/i.test(type));
      if (!valid) { await response.body?.cancel(); return false; }
      await response.arrayBuffer();
      return true;
    }));
    return checks.every(result => result.status === 'fulfilled' && result.value === true);
  } catch { return false; }
}
