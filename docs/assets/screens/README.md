# Screenshots

Website preview assets live with the website in this repository. No separate
assets repository or distribution mirror is needed.

The current set contains 57 captures: Home, Player and Library for the default
StreamX appearance and all 18 published skins. Captured on Android on
2026-10-27 at 1220 × 2712, in dark mode, and exported as WebP (quality 86).

Default StreamX look (also used in the hero):

    home.webp  player.webp  library.webp

Skins go in a folder named after the skin id from `extensions/index.json`:

    skins/<skin id>/home.webp
    skins/<skin id>/player.webp
    skins/<skin id>/library.webp

A skin appears in the website's skin picker as soon as its `home.webp` exists.
The picker then cycles Home → Player → Library for each skin.

Keep all three screens together when adding or refreshing a skin. Use the
catalogue's exact id (for example, `ipod` for iPod Classic), since `tour.js`
discovers these paths automatically. The default Home image also fills the
website hero. Publishing `/docs` through GitHub Pages deploys these assets
with the website.
