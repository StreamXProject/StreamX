'use strict';
(() => {
  const root = document.documentElement;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const fine = matchMedia('(hover: hover) and (pointer: fine)').matches;

  // Screenshot slots. The shimmer placeholder (labelled by data-label) stays visible until an image loads.
  // setShot swaps images with a crossfade and resolves once the new image is showing (or missing).
  window.streamxSetShot = (screen, src, label) => new Promise(resolve => {
    screen.dataset.label = label;
    const old = [...screen.querySelectorAll('img')];
    const img = new Image();
    img.alt = label; img.decoding = 'async';
    const finish = ok => {
      for (const o of old) { o.classList.add('out'); setTimeout(() => o.remove(), 650); }
      if (ok) { screen.append(img); requestAnimationFrame(() => requestAnimationFrame(() => img.classList.add('loaded'))); }
      resolve(ok);
    };
    img.onload = () => (img.decode ? img.decode().catch(() => {}) : Promise.resolve()).then(() => finish(true));
    img.onerror = () => finish(false);
    img.src = src;
  });
  for (const img of document.querySelectorAll('img[data-shot]')) {
    const screen = img.parentElement;
    screen.dataset.label = img.dataset.shot;
    const done = () => requestAnimationFrame(() => img.classList.add('loaded'));
    if (img.complete && img.naturalWidth) done();
    else img.addEventListener('load', done, {once: true});
    img.addEventListener('error', () => img.remove(), {once: true});
  }

  // Theme toggle with a circular reveal where supported.
  const toggle = document.getElementById('theme-toggle');
  const isLight = () => root.dataset.theme ? root.dataset.theme === 'light' : matchMedia('(prefers-color-scheme: light)').matches;
  const label = () => toggle.setAttribute('aria-label', isLight() ? 'Switch to dark theme' : 'Switch to light theme');
  label();
  toggle.addEventListener('click', e => {
    const next = isLight() ? 'dark' : 'light';
    const apply = () => { root.dataset.theme = next; label(); try { localStorage.setItem('theme', next); } catch {} };
    if (reduced || !document.startViewTransition) return apply();
    const x = e.clientX || innerWidth - 40, y = e.clientY || 30;
    const r = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
    document.startViewTransition(apply).ready.then(() => {
      root.animate({clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${r}px at ${x}px ${y}px)`]},
        {duration: 650, easing: 'cubic-bezier(.22,1,.36,1)', pseudoElement: '::view-transition-new(root)'});
    });
  });

  // Scroll reveal.
  const revealer = new IntersectionObserver(entries => {
    for (const entry of entries) if (entry.isIntersecting) { entry.target.classList.add('in'); revealer.unobserve(entry.target); }
  }, {rootMargin: '0px 0px -6% 0px', threshold: .1});
  window.streamxReveal = el => revealer.observe(el);
  document.querySelectorAll('.reveal').forEach(window.streamxReveal);
  requestAnimationFrame(() => document.querySelector('.headline').classList.add('in'));

  // Nav state, progress bar and active section link.
  const nav = document.getElementById('nav');
  const progress = document.querySelector('.progress');
  let ticking = false;
  const onScroll = () => {
    ticking = false;
    const y = scrollY, max = root.scrollHeight - innerHeight;
    nav.classList.toggle('scrolled', y > 12);
    progress.style.setProperty('--p', max > 0 ? (y / max).toFixed(4) : 0);
  };
  addEventListener('scroll', () => { if (!ticking) { ticking = true; requestAnimationFrame(onScroll); } }, {passive: true});
  onScroll();
  const links = [...document.querySelectorAll('nav a[href^="#"]')];
  const sections = new IntersectionObserver(entries => {
    for (const entry of entries) if (entry.isIntersecting)
      for (const a of links) a.classList.toggle('active', a.getAttribute('href') === `#${entry.target.id}`);
  }, {rootMargin: '-45% 0px -50% 0px'});
  links.forEach(a => { const s = document.querySelector(a.getAttribute('href')); if (s) sections.observe(s); });

  if (reduced || !fine) return;

  // Hero phone tilt.
  const tilt = document.querySelector('[data-tilt]');
  const hero = document.querySelector('.hero');
  hero.addEventListener('pointermove', e => {
    const r = hero.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
    tilt.style.transform = `rotateY(${x * 14}deg) rotateX(${-y * 10}deg)`;
  });
  hero.addEventListener('pointerleave', () => { tilt.style.transform = ''; });

  // Cursor spotlight on cards.
  document.addEventListener('pointermove', e => {
    const card = e.target.closest?.('.spot');
    if (!card) return;
    const r = card.getBoundingClientRect();
    card.style.setProperty('--mx', `${e.clientX - r.left}px`);
    card.style.setProperty('--my', `${e.clientY - r.top}px`);
  }, {passive: true});

  // Magnetic buttons.
  document.addEventListener('pointermove', e => {
    const el = e.target.closest?.('.magnetic');
    if (!el) return;
    const r = el.getBoundingClientRect();
    el.style.transform = `translate(${(e.clientX - r.left - r.width / 2) * .18}px, ${(e.clientY - r.top - r.height / 2) * .3}px)`;
  }, {passive: true});
  document.addEventListener('pointerout', e => {
    const el = e.target.closest?.('.magnetic');
    if (el && !el.contains(e.relatedTarget)) el.style.transform = '';
  });
})();
