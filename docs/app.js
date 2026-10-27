'use strict';
const repository = 'StreamXProject/StreamX';
const modelRepository = 'StreamXProject/StreamX-Releases';
const releaseRoot = `https://github.com/${repository}/releases`;
const date = value => new Intl.DateTimeFormat('en', {dateStyle: 'medium'}).format(new Date(value));
const megabytes = bytes => `${(bytes / 1048576).toFixed(1)} MB`;
const assets = release => release.assets.filter(asset => asset.state === 'uploaded');
function link(asset, label) {
  const a = document.createElement('a');
  // Only public assets from the app and legacy model repositories are permitted.
  const url = new URL(asset.browser_download_url);
  if (url.origin !== 'https://github.com' || ![repository, modelRepository].some(repo => url.pathname.startsWith(`/${repo}/releases/download/`))) return null;
  a.href = url.href;
  a.textContent = label;
  return a;
}
function apkLinks(release, target) {
  const apks = assets(release).filter(a => a.name.endsWith('.apk') && a.name !== 'StreamX.apk');
  const elements = apks.map(asset => {
    const abi = ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'].find(abi => asset.name.includes(abi));
    return link(asset, `${abi || 'APK'} · ${megabytes(asset.size)} ↓`);
  }).filter(Boolean);
  if (elements.length) document.getElementById(target).replaceChildren(...elements);
}
async function loadReleases() {
  try {
    const response = await fetch(`https://api.github.com/repos/${repository}/releases?per_page=100`, {headers: {Accept: 'application/vnd.github+json'}});
    if (!response.ok) throw new Error(`GitHub ${response.status}`);
    const releases = await response.json();
    const appReleases = releases.filter(r => !r.draft && /^v\d+(?:\.\d+)+(?:-[\w.-]+)?$/.test(r.tag_name) && assets(r).some(a => a.name.endsWith('.apk')));
    const stable = appReleases.find(r => !r.prerelease);
    if (stable) {
      const apk = assets(stable).find(a => a.name === 'StreamX.apk' || a.name === 'StreamX-arm64-v8a.apk');
      const download = apk && link(apk, '');
      if (download) for (const a of document.querySelectorAll('#download, [data-download-mirror]')) a.href = download.href;
      document.getElementById('release-notes').href = `${releaseRoot}/tag/${encodeURIComponent(stable.tag_name)}`;
      document.getElementById('version').textContent = `${stable.tag_name} · Android 8.0+ · ARM64${apk ? ` · ${megabytes(apk.size)}` : ''}`;
      document.getElementById('stable-title').textContent = `StreamX ${stable.tag_name.replace(/^v/, '')}`;
      document.getElementById('stable-detail').textContent = `Released ${date(stable.published_at)} · SHA-256 checksums included`;
      apkLinks(stable, 'stable-assets');
    }
    const modelResponse = await fetch(`https://api.github.com/repos/${modelRepository}/releases?per_page=100`);
    if (!modelResponse.ok) throw new Error(`Models HTTP ${modelResponse.status}`);
    const modelReleases = await modelResponse.json();
    const model = modelReleases.filter(r => !r.draft && /^automix-\d+$/.test(r.tag_name)).sort((a, b) => Number(b.tag_name.slice(8)) - Number(a.tag_name.slice(8)))[0];
    if (model) {
      document.getElementById('automix-release').href = `https://github.com/${modelRepository}/releases/tag/${model.tag_name}`;
      document.getElementById('model-version').textContent = `Package ${model.tag_name.slice(8)} · Engine + models`;
      const packages = assets(model).filter(a => /^streamx-automix-\d+-(arm64-v8a|armeabi-v7a|x86_64)\.zip$/.test(a.name));
      const elements = packages.map(asset => {
        const abi = asset.name.match(/(arm64-v8a|armeabi-v7a|x86_64)\.zip$/)[1];
        return link(asset, `${abi}　·　${megabytes(asset.size)}　↓`);
      }).filter(Boolean);
      if (elements.length) document.getElementById('model-assets').replaceChildren(...elements);
    }
  } catch (error) {
    // The static download and release links remain usable when the API is unavailable.
    console.info('Live release details unavailable.', error.message);
  }
}
loadReleases();
