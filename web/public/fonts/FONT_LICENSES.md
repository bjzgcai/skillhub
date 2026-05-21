# Bundled Web Fonts

This directory contains local web-font assets used by SkillHub so the runtime page no longer depends on Google Fonts.

- Source Han Sans SC local family uses `@fontsource/noto-sans-sc` files. Noto Sans CJK and Source Han Sans share the same open-source typeface design for Simplified Chinese. License: SIL Open Font License 1.1.
- JetBrains Mono uses `@fontsource/jetbrains-mono` files. License: SIL Open Font License 1.1.

Upstream package metadata was fetched from npm only at build/development time; browser runtime loads these files from `/fonts/...`.
