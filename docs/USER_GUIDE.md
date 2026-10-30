# User guide

Tenseven is a launcher for phones with a physical keyboard. This guide covers what you see and what the keys do. It describes a beta: only the Unihertz Titan 2 has been tried by hand, and the newest features have had the least testing.

## First run

1. Install the APK and press Home. Choose **Tenseven** and **Always**.
2. The setup screens ask for each optional permission and say why. Every one can be skipped; the launcher works without them.
3. If Android greys out a permission after a sideload, open Android's app info for Tenseven, tap the three dots, and choose **Allow restricted settings**. Setup explains this when it matters.

## Choosing a home screen

Open **Launcher Settings** (BlackBerry 10: the gear icon; BlackBerry 7: menu, then Launcher Settings; or type "launcher settings" in Command). Pick:

- **BlackBerry 10**: a column of live tiles, with Hub one swipe to the right.
- **BlackBerry 7**: a see-through banner and tray over your wallpaper, and a grid of icons.

You can change your mind any time. Settings are saved as you tap.

## BlackBerry 7 home

- **Tray panels**: Favorites, All, Downloads, and Frequent (the apps you open most). Add Frequent from the menu.
- **Add an icon to the home screen**: long-press empty space, or menu, then **Add apps to the home screen**. Long-press an icon for **Remove from home screen**.
- **Menu**: there is no Menu key on the Titan 2, so use **Alt+Enter**, the ⋮ button, or a long-press.
- **Widget strip** under the banner: Calendar and Now Playing by default; Alarm can be added. Switch them in Launcher Settings.
- **Icon size**: Small, Medium or Large in Launcher Settings.
- **Command and Hub** open as BlackBerry 10 views and bring you back to BlackBerry 7 when you leave them.

## BlackBerry 10 home

Tiles for your default phone, messages, mail, calendar and music apps, plus battery and glance cards. Swipe down for Command, swipe right for Hub. Edit Home changes which tiles show.

## Finding things

- **Type on Home** to search apps. It can be turned off in Launcher Settings. **Alt+Space** opens Command from either layout.
- **Hold a letter** to open the app or contact assigned to it. In Command, `assign` and `unassign` set letters. Without an assignment, the most-used app starting with that letter comes first.
- **Command prefixes**: `-` makes a to-do, `/` a note, `+` an event, a web address opens the browser. To-dos and notes go to an app you pick; Tenseven stores none.

## Hub

Messages and mail notifications grouped by person, with inline reply where the app allows it. It reads notifications (needs notification access), keeps them in memory only, and clears them when the launcher stops. Filters, mark read and snooze are in the Hub bar.

## Sound

The speaker icon on both homes shows the ringer mode. Tap to cycle Sound, Vibrate and Silent. Silent needs **Do Not Disturb access**; without it the icon skips Silent, and a long-press takes you to the access screen. The bell shows the unread notification count and opens Hub.

If you switch on **Sound Profiles** in Extras, tapping the speaker opens it (Normal, Loud, Vibrate only, Phone calls only, All alerts off) and a long-press cycles quickly. Changing tones and vibration needs **Modify system settings**.

## Extra apps

Calc Plus, Notes, Sound Profiles and ten games are built in but switched off. Turn them on in the Extras step of setup (Launcher Settings, then Setup, runs it again); they then appear like any other app. Turning them off hides them again.

## Default apps and weather

Tiles and icons open whatever app your phone uses for calls, messages, mail, camera, calendar, browser, gallery and music. Tenseven has **no weather tile**, because it never connects to the internet. Live weather will need Android widget support, which is planned.

## Backup

Launcher Settings, then **Backup**, saves or restores your settings as a file through Android's file picker. Letter assignments and speed dial are not included, because they hold contact names.

## Privacy

No network access, no analytics, no accounts. Optional permissions: notification access (Hub, Now Playing), usage access (recent apps), calendar (agenda), contacts (names), Do Not Disturb and system settings (sound). The accessibility service for double-tap to lock is optional and reads nothing.

## Troubleshooting

- **A permission is greyed out**: use Allow restricted settings (above).
- **Silent mode will not stick**: grant Do Not Disturb access.
- **Wrong home after an update**: press Home and choose Tenseven, then **Always**.
- Report problems with the device, Android version and steps on the GitHub issue tracker.
