# L0 lean Launcher candidate

The Launcher Core, Phone and Calendar are independent development APKs. The
owner accepted all eight Titan 2 functional areas for source fingerprint
`bfa906a7a903ecc65e6e85ea93c4107496abd712accb845ba44cbd7c5f6814df`.
That acceptance covers the existing BBOS7/10 homes, notifications, Peek, lock
screen, keyboard/trackpad, Active Frames, widgets, Phone, Calendar and startup
without companions. It does not constitute acceptance of later source changes.

## Settings and BBOS7 Recent

Settings now has six numbered pages: Permissions, Appearance & Home, Hub, Lock
screen, Active Frames, and Review & Finish. Optional companion status is a
separate entry. The planner remains informational; it does not install APKs or
grant roles. Old Apps & Features links open companion status. Type to search
retains its existing preference under Appearance & Home; device compatibility
is under Permissions. Existing bundled-app choices remain stored for recovery
but no longer enable embedded Activities from Launcher Settings.

BBOS7's default panel order is Favorites, All, Recent, Downloads. Frequent is
separate and optional. Recent resolves locally recorded Launcher launches
against currently available personal-profile applications, newest first,
without requesting usage access. The existing Remember returned apps setting
disables both recording and Recent display. Disabling it does not silently
delete older records. Missing/uninstalled applications are omitted.

On first reading an older panel list, Recent is inserted before Downloads, or
after All, or at the end. Other saved order and disabled panels are preserved.
Saving records a trailing version marker so explicitly hiding Recent remains
effective. Older Launchers ignore that unknown marker. Reading alone does not
rewrite preferences. Touch, panel arrows, Space and Alt+Left/Right use the
existing tray controls; arrows/Enter use the existing grid navigation.

The owner subsequently confirmed all six Settings/BBOS7 Recent checks PASS.
See [L0-R1 owner acceptance](L0_R1_TITAN2_ACCEPTANCE.md). The checks covered
six-page Back/Next and
old links, Type to search persistence, default and custom panel orders,
Recent hide/re-enable, launches and return refresh, uninstallation, empty
history, privacy opt-out, and physical keyboard/trackpad navigation. Emulator
and JVM evidence is retained in the local qualification deliverable. It is
not a substitute for these device checks or the full remote Frames matrix.

## Titan 2 technical references

- [Owner's device research](https://github.com/aimindseye/unihertz-titan2)
- [Titan 2 documentation](https://github.com/Serlk/titan2-docs)
- [Titan 2 tools research](https://github.com/Serlk/titan2-tools)
- [Titan 2 boot research](https://github.com/Serlk/titan2-boot)
- [Titan 2 TWRP research](https://github.com/Serlk/titan2-twrp)

These are technical references for device characteristics and keyboard research.
Launcher development uses public Android APIs and ordinary user-granted
permissions. Rooting, bootloader unlocking, flashing, and TWRP are neither
required nor authorized. No firmware tools or code are imported by this change.

## Integration and release holds

The source remains uncommitted until the exact four-module quality-policy
transition has independent approval and mandatory gates pass. Existing analyzer
and suppression inventories remain blocking; device acceptance does not waive
them. Production-signed Sable Watch interoperability, existing-user migration
and rollback, production release signing, and the separate Tasks, Notes,
Calculator and Profiles APKs remain separate release work. Do not replace an
existing signed Launcher or migrate its records using these development APKs.
