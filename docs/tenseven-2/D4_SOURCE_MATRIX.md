# D4 — Source and scope matrix

| Surface | Files changed | Protected boundary |
|---|---|---|
| Home status choice | `model/HomeStatusMode.kt`, `platform/StartStateRepository.kt`, `SettingsSections.kt` | Existing saved dial sets and per-Home preference keys preserved |
| BB10 status header | `ui/Bb10HomeChrome.kt`, `ui/BbHomeStatus.kt` | `ui/ActiveFramesPage.kt` untouched; only existing status-row composable implementation updated |
| BB7 header and Favorites title | `ui/Bb7HomeScreen.kt`, `ui/BbHomeStatus.kt` | Existing favorites, desktop shortcuts, task/menu/gesture paths preserved |
| Common lower bar | `ui/ReferenceHomeBar.kt` | Shared bar already used in BB10 and BB7; no new bar fork |
| Regression test | `model/HomeStatusModeTest.kt` | D4 pure mode decode and independent preference keys |
| D5 | No Calendar / Phone business logic shipped in D4 | Remains separately gated |

Design uses reference PDF pp.1–2, 15–17. The *example* Q25 interface is included as an acceptance target, not as a device already tested. Active Frames implementation was deliberately not changed. Default BB10 Dials uses the approved three core metrics; old extra dial settings stay saved but are not rendered as colored rings by the new compact design. All sampled values are best effort, sourced from Android and shown as `–` if unavailable.
