# Explicit English fallback inventory

StreamX currently has untranslated strings for server onboarding, playlist transfer and extension management. Android resolves missing localized strings from the default English resources. These inventories record that existing limitation; they are not translations.

`english-fallbacks.txt` records existing gaps across the supported languages. `chinese-english-fallbacks.txt` records additional Chinese gaps. LocaleStringsTest still rejects missing keys outside these lists, deleted base keys in these lists, and entries that have since been translated throughout their language group. Do not add new omissions merely to make a build pass. Translate new UI strings and shrink these inventories as translation work proceeds.
