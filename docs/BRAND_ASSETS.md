# Brand assets: logos of other companies' apps

Tenseven can show a logo for a messaging or AI app that is **not installed** (for example in a picker that lists
supported apps, with a link to where to get it). The approach is the one MinkLauncher OpenSource uses
(`docs/messaging-provider-assets.md` and `docs/ai-provider-assets.md` upstream), adopted here so the two projects
stay comparable and reviewable.

## Rules

1. **An installed app always shows the icon Android gives us** (`PackageManager` / `LauncherApps`), never a
   bundled copy. The bundled file only identifies an app that is not there.
2. **Use only the developer's own artwork,** from the app's official listing (the 512 x 512 icon a Google Play
   page publishes as `og:image`), or from the project's official source repository when it is not on Play. The
   source of every file is recorded in [`brand-assets/CATALOG.tsv`](../brand-assets/CATALOG.tsv) with its
   sha256 and status.
3. **Nominative use only.** The name and icon say which app is meant. They do not imply endorsement,
   sponsorship or partnership; `NOTICE` says so, and the marks remain their owners'.
4. **No recolouring, redrawing, cropping into a new mark, or use as Tenseven's own branding.**
5. **Nothing is bundled without a catalog row,** and `tests/run.sh` fails if an artwork file is not in the
   catalog or does not match its recorded sha256.
6. **Unavailable providers link to the provider's own install page,** not assuming one store.
7. **Remove on request.** If an owner asks, delete the file and set the row back to `pending`; open an issue
   or see `SECURITY.md` for the contact.

## Catalog

`brand-assets/CATALOG.tsv`, one row per provider: `kind` (messaging or ai), `name`, `packages` (runtime
package names, comma separated, first is canonical), `source` (`play`, `appstore` or `vendor`), `source_url`,
`install_url`, `file` (resource name in `app/src/main/res/drawable-nodpi/`, without extension), `status`
(`pending` or `bundled`) and `sha256`.

It was seeded from Mink's provenance tables: 28 providers, **all pending: no artwork is in this repository
yet** and Mink's image files were not copied (their provenance depends on Mink's own downloads, and fetching
them again here keeps this repository's record first-hand).

## Getting the artwork

```bash
python3 tools/brand_assets.py fetch               # Google Play rows: downloads og:image, marks them bundled
python3 tools/brand_assets.py fetch --only com.whatsapp
python3 tools/brand_assets.py check               # what CI runs
```

`appstore` and `vendor` rows (for example Microsoft Copilot from the App Store, Molly from its source
repository) are saved by hand as `<file>.png` in `drawable-nodpi/`, then `python3 tools/brand_assets.py record`.
Review the images before committing: a listing can change, and a JPEG from a listing is kept as `.jpg`.

Run it on a machine with internet access. The sandbox this repository was scaffolded in could not reach
Google Play, which is why the catalog is pending.
