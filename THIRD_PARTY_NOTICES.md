# Third-party notices

Everything in this repository is Apache-2.0 (see `LICENSE`) except the items below, which keep their
own licences. Licence texts that ship with a component are kept next to it.

| Component | Path | Licence | Source |
|---|---|---|---|
| Tabler Icons outline glyphs (38 verbatim SVGs listed with sha256 in `ICON_MANIFEST.tsv`), and the Kotlin table and launcher-icon vectors generated from them (`TablerGlyphPaths.kt`, `ic_tenseven_*.xml`) | `third_party/tabler-icons/` | MIT (`LICENSE` in that folder) | https://github.com/tabler/tabler-icons, npm `@tabler/icons` 3.50.0 |
| MinkLauncher OpenSource, reference snapshot (not compiled) | `third_party/minklauncher/` | Apache-2.0 (`LICENSE` and `NOTICE` in that folder) | https://github.com/katoaapps/openminilaunch @ `b9b26f73` |
| Conversation grouping and notification listener, ported and changed from MinkLauncher OpenSource (Katoa Apps) | `app/src/main/java/io/github/aimindseye/tenseven/conversations/` | Apache-2.0 (`third_party/minklauncher/LICENSE`; each file carries a header naming its source and changes) | https://github.com/katoaapps/openminilaunch @ `b9b26f73` |
| Titan AI status rings: ring drawing and device stats sampler, ported and trimmed (via SableOS) | `app/src/main/java/io/github/aimindseye/tenseven/ui/SableStatusRings.kt`, `.../platform/DeviceStatsSampler.kt` | MIT (`third_party/titan-ai/LICENSE`; notice kept in each file header) | https://github.com/andrehafner/aiassistant @ `2f4b5d76` |
| Sable Start and Sable design sources, imported from SableOS | `app/`, `design/` | Apache-2.0 (same licence as this repository) | https://github.com/aimindseye/zinwaq25-sableos @ `31420f59` |
| detekt rule set, byte-exact copy from SableOS | `config/detekt.yml` | Apache-2.0 | https://github.com/aimindseye/zinwaq25-sableos |
| Gradle wrapper | `gradle/wrapper/`, `gradlew`, `gradlew.bat` | Apache-2.0 | https://gradle.org, version 8.13 |

## Fetched at build time, not in this repository

| Component | Licence |
|---|---|
| Android Gradle Plugin, Kotlin, Jetpack Compose, AndroidX, kotlinx.coroutines | Apache-2.0 |
| JUnit 4 | EPL-1.0 |
| detekt, ktlint, Kover | Apache-2.0, MIT, Apache-2.0 |

## Bundled brand artwork

None yet. When provider icons are added (`python3 tools/brand_assets.py fetch`), each is listed with its
source in `brand-assets/CATALOG.tsv`, and the policy is in [`docs/BRAND_ASSETS.md`](docs/BRAND_ASSETS.md).

## Translations

The Sable Start and design strings carry machine-assisted draft translations in 18 languages, imported
from SableOS. They have not been reviewed by fluent speakers; English is the reference text.
