'use strict';
const pluginNames = {applemusic: 'Apple Music', spotify: 'Spotify', cloudstream: 'CloudStream', amazonmusic: 'Amazon Music'};
const displayName = (entry) => pluginNames[entry.type === 'plugin' && entry.id] || entry.name.replace(/^\p{Ll}/u, c => c.toUpperCase());
const logoUrl = (entry) => `assets/logos/${entry.type === 'plugin' ? 'plugins' : 'skins'}/${encodeURIComponent(entry.id)}.svg`;

window.streamxExtensionManifest = (async () => {
  const response = await fetch('https://streamxproject.github.io/StreamX-Releases/extensions/index.json', {cache: 'no-cache'});
  if (!response.ok) throw new Error(`Manifest HTTP ${response.status}`);
  const manifest = await response.json();
  if (manifest.schemaVersion !== 1 || !Array.isArray(manifest.extensions) || manifest.extensions.length > 500) throw new Error('Invalid extension manifest');
  for (const entry of manifest.extensions) {
    const url = new URL(entry.apkUrl);
    if (!['plugin', 'skin'].includes(entry.type) || !/^[\w.-]+$/.test(entry.id) || url.origin !== 'https://github.com' ||
        ![entry.type === 'skin' ? 'Skins' : 'StreamX-Releases', 'StreamX-Releases'].some(repo => url.pathname.startsWith(`/StreamXProject/${repo}/releases/download/`))) throw new Error('Invalid public extension');
  }
  return manifest;
})();

// Newest version of each extension, grouped as {plugins, skins}; every item lists its APK variants.
window.streamxCatalogue = window.streamxExtensionManifest.then(manifest => {
  const groups = new Map();
  for (const entry of manifest.extensions) {
    if (!groups.has(entry.packageName)) groups.set(entry.packageName, []);
    groups.get(entry.packageName).push(entry);
  }
  const plugins = [], skins = [];
  for (const variants of groups.values()) {
    const newest = Math.max(...variants.map(entry => entry.versionCode));
    const current = variants.filter(entry => entry.versionCode === newest);
    const first = current[0];
    const item = {id: first.id, type: first.type, name: displayName(first), version: first.versionName, logo: first.iconUrl || logoUrl(first), variants: current};
    (first.type === 'plugin' ? plugins : skins).push(item);
  }
  skins.sort((a, b) => a.name.localeCompare(b.name));
  return {plugins, skins};
});

function logo(item, className = 'logo') {
  const box = document.createElement('span');
  box.className = className;
  box.setAttribute('aria-hidden', 'true');
  box.textContent = item.name.charAt(0);
  const img = new Image();
  img.alt = ''; img.decoding = 'async';
  img.onload = () => box.replaceChildren(img);
  img.src = item.logo;
  return box;
}
window.streamxLogo = logo;

(async () => {
  const input = document.getElementById('repository-url');
  const button = document.getElementById('copy-repository');
  let reset = 0;
  button.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(input.value);
      document.getElementById('copy-status').textContent = 'Copied.';
      button.classList.remove('copied'); void button.offsetWidth; button.classList.add('copied');
      button.firstElementChild.textContent = 'Copied ✓';
      clearTimeout(reset);
      reset = setTimeout(() => { button.classList.remove('copied'); button.firstElementChild.textContent = 'Copy link'; }, 2200);
    } catch {
      input.focus(); input.select();
      document.getElementById('copy-status').textContent = 'Link selected. Copy it from the box.';
    }
  });
  try {
    const {plugins, skins} = await window.streamxCatalogue;
    const reveal = el => window.streamxReveal ? window.streamxReveal(el) : el.classList.add('in');
    const pluginCards = plugins.map((item, i) => {
      const article = document.createElement('article');
      article.className = 'public-plugin reveal spot';
      article.style.setProperty('--d', `${i * .08}s`);
      const top = document.createElement('div'); top.className = 'plugin-top';
      const text = document.createElement('div');
      const name = document.createElement('h3'); name.textContent = item.name;
      const detail = document.createElement('p'); detail.textContent = `v${item.version}`;
      text.append(name, detail);
      top.append(logo(item), text);
      const links = document.createElement('div'); links.className = 'asset-links';
      for (const entry of item.variants) {
        const a = document.createElement('a'); a.href = entry.apkUrl;
        a.textContent = `${entry.abis.length ? entry.abis.join(', ') : 'APK'} · ${(entry.size / 1048576).toFixed(1)} MB ↓`;
        links.append(a);
      }
      article.append(top, links);
      return article;
    });
    const skinCards = skins.map((item, i) => {
      const a = document.createElement('a');
      a.className = 'skin-card reveal spot'; a.href = item.variants[0].apkUrl; a.id = `skin-${item.id}`;
      a.style.setProperty('--d', `${Math.min(i, 12) * .03}s`);
      const text = document.createElement('div');
      const name = document.createElement('b'); name.textContent = item.name;
      const detail = document.createElement('small'); detail.textContent = `v${item.version}`;
      text.append(name, detail);
      const dl = document.createElement('span'); dl.className = 'dl'; dl.setAttribute('aria-hidden', 'true'); dl.textContent = '↓';
      a.setAttribute('aria-label', `Download ${item.name} skin`);
      a.append(logo(item), text, dl);
      return a;
    });
    if (pluginCards.length) { document.getElementById('public-plugins').replaceChildren(...pluginCards); pluginCards.forEach(reveal); }
    if (skinCards.length) {
      document.getElementById('public-skins').replaceChildren(...skinCards); skinCards.forEach(reveal);
      document.getElementById('public-skin-count').textContent = `· ${skinCards.length}`;
    }
  } catch (error) { console.info('Extension downloads unavailable.', error.message); }
})();
