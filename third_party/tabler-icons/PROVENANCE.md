# Tabler Icons

| | |
|---|---|
| Upstream | https://github.com/tabler/tabler-icons |
| Package | `@tabler/icons` 3.50.0 from the npm registry (`npm pack @tabler/icons@3.50.0`) |
| Tarball sha256 | `4c2eb678ed71cdcbf57a3e899c4e91f3445d680fbc1d57d27c7083a321c47303` |
| Licence | MIT, Copyright (c) 2020-2026 Paweł Kuna (`LICENSE` in this folder, copied unchanged) |
| Set used | `icons/outline`: 24 x 24, stroke 2, round caps and joins |
| Imported | 2026-10-29 |

Only the icons Tenseven draws are copied, byte for byte, into `icons/outline/`; `ICON_MANIFEST.tsv`
lists each with its sha256 and what it is used for. The Kotlin glyph table
(`app/src/main/java/com/sable/start/model/TablerGlyphPaths.kt`) and the launcher icon vectors
(`app/src/main/res/drawable/ic_tenseven_*.xml`, `mipmap-anydpi-v26/ic_tenseven*.xml`) are generated
from those SVGs by `tools/tabler_icons.py`; CI fails if they drift.

## Changing or adding an icon

1. Add the name to `AppGlyph` in `AppGlyphs.kt` (or `EXTRA_ICONS` in the tool).
2. `npm pack @tabler/icons@3.50.0 && tar xzf tabler-icons-3.50.0.tgz`
3. `python3 tools/tabler_icons.py import --from package`
4. `python3 tools/tabler_icons.py check`, then commit the SVGs, the manifest and the generated files together.

To move to a newer Tabler release, change the version here and in the tool's docstring, re-import, and read
the diff of the SVGs: icon names are renamed upstream from time to time.

## Why not Phosphor

SableOS draws its glyphs from Phosphor Icons. Tenseven uses Tabler so the two projects do not look alike.
