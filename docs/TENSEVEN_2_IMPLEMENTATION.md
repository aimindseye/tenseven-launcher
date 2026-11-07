# TenSeven Launcher 2.0 implementation

The owner approved the paired Titan 2 / Zinwa Q25 designs in PR #124. This is a
functional adaptation of SableOS 2.0, with a BlackBerry black/white/blue theme, not
only a visual reskin. Existing user themes, icon packs and saved configuration stay available.

## Source audit

SableOS was reviewed at commit `a3300bd96f4cb7a70c980ec50191edff0907c8ca`:

- [SableOS 2.0 release functionality](https://github.com/aimindseye/zinwaq25-sableos/blob/a3300bd96f4cb7a70c980ec50191edff0907c8ca/releases/2.0.md)
- [Phone implementation and its boundaries](https://github.com/aimindseye/zinwaq25-sableos/blob/a3300bd96f4cb7a70c980ec50191edff0907c8ca/docs/implementation/sable-phone.md)
- PhoneActivity, DialPadView and the Phone manifest under
  `sable-src/apps/titan2/platform/phone/src/main/` (the current source includes Favorites).
- Calendar MainActivity under `sable-src/apps/r8/android/calendar/src/main/`.

The supplied Phone photographs add the Calls / Favorites / Contacts / Dial pad tabs,
visible Menu, name/number search and Phone menu to the acceptance reference. They are
not copied into the application or used as proof of TenSeven functionality.

## Deliveries

1. Shared visual foundation, device-aware lower navigation, Hub panels and per-app lock
   selection. Keep the last-eight-opened frame policy, explicit screen-capture consent,
   existing notification source selection, reminders and calendar sources.
2. TenSeven-owned Phone browsing and Calendar surfaces, using ordinary Android APIs.
3. Optional default-dialer integration, independently tested on real phones before release.
4. Remaining portable SableOS features selected from the mapping below, followed by
   device qualification, genuine screenshots and 2.0 release packaging.

The 2.0 release is not complete merely because delivery 1 is merged. No release version
or public APK is changed by this implementation work.

## Phone functional scope

| Feature | TenSeven 2.0 approach |
|---|---|
| Calls / Favorites / Contacts / Dial pad / Menu | TenSeven-owned Phone surface matching the approved theme; touch and keyboard access on both devices |
| Name, initials and number search | Adapt Sable's pure search policy; query only permitted Contacts/CallLog sources |
| Floating dialpad | Bottom sheet within Phone, over the current list; expand/collapse, number entry, delete/hold-to-clear, `+`, `*`, `#`, matching contacts and Call; Back closes it first; no system-wide overlay permission |
| Hardware digits | Use the actual key character map and verified device mapping; do not hard-code Q25 positions on Titan 2 |
| Letter speed dial | Reuse TenSeven's existing explicit assignments and hold/short-press distinction; do not read Sable's protected provider |
| Multiple contact numbers | Explicit number chooser before handing off or placing a call |
| Grouped call history and missed calls | Read only with permission and a supported dialer-role path; do not claim access while it is denied |
| Call / SMS / edit number / copy / add contact | Explicit actions through public APIs/intents, with no call triggered by browsing a row |
| Space answers calls; Call/End, mute/speaker/hold/DTMF | Optional default-dialer role plus InCallService and real call-state handling; launcher key handlers cannot provide global in-call shortcuts |
| SIM choice / calling accounts | Public Telecom APIs, active accounts and capability-aware actions; no signature permission import |
| Blocked numbers / call settings | Open supported system management screens; direct blocked-number access requires the appropriate role |
| Incoming/ongoing/missed-call notifications | Default-dialer delivery, respecting notification/full-screen intent requirements; test ringing, Bluetooth, proximity and multiple calls |
| IMEI / special service codes | Keep privileged IMEI access out; service codes stay with the installed/system dialer until a supported public route is qualified |
| Emergency calling | Preserve the system emergency route; never import the ROM's system-dialer overlay or claim emergency replacement |

The floating sheet is the chosen initial dialpad presentation. An expanded view must
remain usable on Q25 and at large text sizes, with a visible Call action and keyboard focus.

## Other SableOS functionality

| Area | Existing TenSeven / adaptation |
|---|---|
| Hub | Notification-source opt-in, grouped messages/mail, Snooze, Remind and Upcoming already exist. Add the approved settings/action panels. Priority ordering and Mark prior read need separate policies and tests before being claimed. |
| Active Frames | Existing media and owned-app summaries stay. Device Monitor summary and additional media controls can use public/local data; automatic third-party snapshots remain unavailable. |
| Calendar | Month busy rings, collapsible month/week strip, agenda/day/week, search, selected calendars and keyboard navigation. Merge overlapping occupied intervals, keep all-day events separate. Begin event changes through Android intents. Full CRUD/ICS import requires its own permission, recurrence and conflict tests. |
| Command / grid | Existing search, folders, hidden apps, tabs and shortcuts remain. Import additional pure policies only with regression coverage. Global Alt+Space from other apps is a ROM feature, not a launcher guarantee. |
| Work/private profiles | Use public LauncherApps and profile capabilities already exposed by Android. Do not create or unlock profiles through hidden APIs. |
| Lock | Per-app rail and inline details. The cover remains over Android keyguard; the initial implementation requires authentication before message content is shown, even for public notifications, and the message-preview opt-in. Selection alone changes no notification state. Picture Password and compact system PIN UI require SystemUI/ROM support. |
| Hardware | Measure window/insets/font scale and input capabilities. A keyboard with Call and End keys gets a compact default Home bar; saved custom app shortcuts survive. Verify Q25 mappings physically. Titan 2 is the currently owner-tested TenSeven device. |
| System features | App sleeping, firmware/wake-key fixes, system trackpad overlays and system-wide key dispatch remain SableOS work. Speech, browser, weather and other standalone app expansions require their own TenSeven product decision and deliverable. |

## Delivery 1 verification

- Keep the no-new-findings ratchet and Sonar/security gates; do not relax baselines.
- Unit tests for compact/default/custom shortcut policy.
- API 34/35 UI checks for Home navigation, per-source selection/redaction and Hub panel actions.
- Device checks: Titan 2 standard/large text; Q25 remains unqualified until measured on hardware.
- Source Sans 3 is bundled from Adobe's release commit
  `87b37a2daaed80fcb8e8ccb0085c4d72ddade12e`; license in `design/SourceSans3-OFL.txt`.

## Dynamic Hub message actions

The right-side panel adapts to the latest selected notification. Email uses Email options / Open
email app; messaging uses Message options / Open messaging app. Reply uses the provider's actual
RemoteInput target and label. Additional Reply All, Forward, Archive or other native actions are
shown only when the provider supplies a usable PendingIntent, with text entry when required.
There is no inference from translated action names and no fabricated email recipients or body.
An email app that exposes none of those actions retains Open email app plus TenSeven's explicit
Mark read, Snooze and Remind actions. Search-by-subject/sender, invitations and contact creation
need separately available structured data; notification text is not treated as a trusted address book.
Provider actions are checked again on activation against the exact active notification/action,
current source opt-in, notification access and Android lock state. Stale or revoked actions fail
without executing. Merely selecting a message, opening an editor or changing sources sends nothing.
