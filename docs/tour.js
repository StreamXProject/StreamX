'use strict';
// Skins tour: shows Home / Player / Library screenshots for StreamX and each skin that has them.
// Screenshots live in assets/screens/ (default look) and assets/screens/skins/<skin id>/.
(() => {
  const $ = id => document.getElementById(id);
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const screens = ['home', 'player', 'library'];
  const screenNames = ['Home', 'Player', 'Library'];
  const cycle = 4500;
  const folder = item => item.id ? `assets/screens/skins/${encodeURIComponent(item.id)}/` : 'assets/screens/';

  const showcase = $('showcase'), picker = $('skin-picker'), pill = document.querySelector('.tab-pill');
  const tabs = [...document.querySelectorAll('[data-shot-tab]')];
  const shots = [...document.querySelectorAll('[data-shot-index]')];
  const title = document.querySelector('.skin-title');
  const timerRing = document.querySelector('.ring-timer');
  const autoplayButton = $('autoplay');
  showcase.style.setProperty('--cycle', `${cycle}ms`);

  const looks = [{id: null, name: 'StreamX', kicker: 'DEFAULT', logo: 'assets/icon.webp'}];
  let look = 0, screen = 0, timer = 0, visible = false, hovering = false, playing = !reduced;

  const movePill = () => {
    const tab = tabs[screen];
    pill.style.setProperty('--x', `${tab.offsetLeft}px`);
    pill.style.setProperty('--w', `${tab.offsetWidth}px`);
  };

  function showScreen(index) {
    screen = (index + screens.length) % screens.length;
    shots.forEach((shot, i) => { shot.dataset.pos = (i - screen + screens.length) % screens.length; shot.setAttribute('aria-hidden', String(i !== screen)); });
    tabs.forEach((tab, i) => { tab.setAttribute('aria-selected', String(i === screen)); tab.tabIndex = i === screen ? 0 : -1; });
    movePill();
    schedule();
  }

  function preload(item) {
    for (const name of screens) { const img = new Image(); img.decoding = 'async'; img.src = `${folder(item)}${name}.webp`; }
  }

  function showLook(index) {
    look = (index + looks.length) % looks.length;
    const item = looks[look];
    [...picker.children].forEach((b, i) => b.setAttribute('aria-pressed', String(i === look)));
    const active = picker.children[look];
    if (active && picker.scrollWidth > picker.clientWidth)
      picker.scrollTo({left: active.offsetLeft - (picker.clientWidth - active.offsetWidth) / 2, behavior: reduced ? 'auto' : 'smooth'});
    title.classList.remove('swap-in'); title.classList.add('swap-out');
    setTimeout(() => {
      $('skin-logo').firstElementChild.src = item.logo;
      $('skin-name').textContent = item.name;
      $('skin-kicker').textContent = item.kicker;
      const get = $('skin-get');
      get.hidden = !item.apk;
      if (item.apk) get.href = item.apk;
      title.classList.remove('swap-out'); void title.offsetWidth; title.classList.add('swap-in');
    }, reduced ? 0 : 280);
    shots.forEach((shot, i) => window.streamxSetShot(shot.firstElementChild, `${folder(item)}${screens[i]}.webp`, `${item.name} · ${screenNames[i]}`));
    if (looks.length > 1) preload(looks[(look + 1) % looks.length]);
    schedule();
  }

  function advance() {
    if (screen < screens.length - 1 || looks.length < 2) showScreen(screen + 1);
    else { showLook(look + 1); showScreen(0); }
  }

  function schedule() {
    clearTimeout(timer);
    timerRing.classList.remove('run');
    if (!(playing && visible && !hovering && !document.hidden)) return;
    void timerRing.offsetWidth; timerRing.classList.add('run');
    timer = setTimeout(advance, cycle);
  }

  function setPlaying(on) {
    playing = on;
    autoplayButton.setAttribute('aria-pressed', String(on));
    autoplayButton.querySelector('.autoplay-label').textContent = on ? 'Pause' : 'Play';
    schedule();
  }

  function addLookButton(item, i) {
    const button = document.createElement('button');
    button.type = 'button';
    button.setAttribute('aria-label', item.name);
    button.setAttribute('aria-pressed', String(i === look));
    button.style.setProperty('--i', i);
    const img = new Image(); img.src = item.logo; img.alt = ''; img.decoding = 'async';
    const tip = document.createElement('span'); tip.className = 'tip'; tip.textContent = item.name;
    button.append(img, tip);
    button.addEventListener('click', () => { if (i !== look) { showLook(i); showScreen(0); } else schedule(); });
    picker.append(button);
  }

  // Events
  tabs.forEach((tab, i) => {
    tab.addEventListener('click', () => showScreen(i));
    tab.addEventListener('keydown', e => {
      const step = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
      if (step) { e.preventDefault(); showScreen(screen + step); tabs[screen].focus(); }
    });
  });
  shots.forEach((shot, i) => shot.addEventListener('click', () => i !== screen && showScreen(i)));
  autoplayButton.addEventListener('click', () => setPlaying(!playing));
  const stack = $('shot-panel');
  stack.addEventListener('pointerenter', e => { if (e.pointerType === 'mouse') { hovering = true; showcase.classList.add('paused'); clearTimeout(timer); } });
  stack.addEventListener('pointerleave', e => { if (e.pointerType === 'mouse') { hovering = false; showcase.classList.remove('paused'); schedule(); } });
  let startX = null, startY = 0;
  stack.addEventListener('touchstart', e => { startX = e.touches[0].clientX; startY = e.touches[0].clientY; }, {passive: true});
  stack.addEventListener('touchend', e => {
    if (startX === null) return;
    const dx = e.changedTouches[0].clientX - startX, dy = e.changedTouches[0].clientY - startY; startX = null;
    if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) showScreen(screen + (dx < 0 ? 1 : -1));
  }, {passive: true});
  new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; schedule(); }, {threshold: .3}).observe(showcase);
  document.addEventListener('visibilitychange', schedule);
  addEventListener('resize', movePill);
  document.fonts?.ready.then(movePill);

  addLookButton(looks[0], 0);
  showScreen(0);
  setPlaying(playing);

  // Add each published skin that has a home screenshot, probing once the tour is close to view.
  const probeSkins = () => window.streamxCatalogue?.then(({skins}) => Promise.all(skins.map(skin => new Promise(resolve => {
    const probe = new Image();
    probe.onload = () => resolve(skin);
    probe.onerror = () => resolve(null);
    probe.src = `${folder(skin)}home.webp`;
  })))).then(found => {
    for (const skin of found.filter(Boolean)) {
      const item = {id: skin.id, name: skin.name, kicker: `SKIN · v${skin.version}`, logo: skin.logo, apk: skin.variants[0].apkUrl};
      looks.push(item);
      addLookButton(item, looks.length - 1);
    }
    picker.hidden = looks.length < 2;
    if (looks.length > 1) preload(looks[1]);
  }).catch(() => { picker.hidden = true; });
  picker.hidden = true;
  const near = new IntersectionObserver(([entry]) => { if (entry.isIntersecting) { near.disconnect(); probeSkins(); } }, {rootMargin: '800px 0px'});
  near.observe(showcase);
})();
