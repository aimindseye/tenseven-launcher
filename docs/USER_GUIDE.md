# User guide

Tenseven is a launcher for phones with a physical keyboard. This guide covers what you see and what the keys do. It describes a beta: only the Unihertz Titan 2 has been tried by hand, and the newest features have had the least testing.

## First run

1. Install the APK and press Home. Choose **Tenseven** and **Always**.
2. The setup screens ask for each optional permission and say why, and offer every choice Launcher Settings has: home screen style, theme and wallpaper, icon grid, widgets, Hub, lock screen and the extra apps. Every step can be skipped; the launcher works without them. The choice steps use the same controls as Launcher Settings and save as you tap.
3. If Android greys out a permission after a sideload, open Android's app info for Tenseven, tap the three dots, and choose **Allow restricted settings**. Setup explains this when it matters.

## Changing setup later

Launcher Settings, then **Setup**, lists every choice with its state: home app, home screen style, contacts, photo counts, notification access, usage access, theme and wallpaper, icon grid, widgets, Hub, lock screen and the extra apps. **Change** opens the place where Android lets you switch it off or change it; extra apps are ticked or unticked on the same screen and saved. A button there opens Launcher Settings.

## Choosing a home screen

Open **Launcher Settings** (BlackBerry 10: the gear icon; BlackBerry 7: menu, then Launcher Settings; or type "launcher settings" in Command). Pick:

- **BlackBerry 10**: a column of live tiles, with Hub one swipe to the right.
- **BlackBerry 7**: a see-through banner and tray over your wallpaper, and a grid of icons.

You can change your mind any time. Settings are saved as you tap.

## BlackBerry 7 home

- **Tray panels**: Favorites, All, Downloads, and Frequent (the apps you open most). Add Frequent from the menu.
- **Icons on the home screen**: the empty space between the banner and the tray holds the icons you put there. Long-press empty space (or menu, then **Add apps to the home screen**) to open All, then long-press an app and choose **Add to home screen**. Long-press a placed icon for **Move** or **Remove from home screen**. Arrow Up from the tray's first row moves into them; Down returns.
- **Favorites** are separate: the tray's first panel. Use **Add to Favorites** / **Remove from Favorites** in an app's menu.
- **Menu**: there is no Menu key on the Titan 2, so use **Alt+Enter**, the ⋮ button, or a long-press.
- **Widget strip** under the banner: Calendar and Now Playing by default; Alarm can be added. Switch them in Launcher Settings.
- **Icon size**: Small, Medium or Large in Launcher Settings.
- **Command and Hub** open as BlackBerry 10 views and bring you back to BlackBerry 7 when you leave them.

## BlackBerry 10 home

**Active Frames** show the apps you opened recently, newest first, up to eight cards in two columns, each with the app's icon and name. Press Enter or tap to open one; the **×**, a long-press, or the X or Backspace key hides that card until you open the app again. Apps you start from Tenseven appear at once; with usage access, apps you opened another way (from a notification, say) appear too. Android only lets the phone's built-in home screen show other apps' live pictures, so on an ordinary install the cards are icons, not live pictures. Until you have opened an app, the home shows the Glance cards (Calendar, Battery, Media and so on) instead; Edit Home changes which. Swipe down for Command, swipe right for Hub.

## Widgets page

Swipe right past Hub (or Alt+Left twice from Home) to the Widgets page. It shows widgets from other apps, such as a weather app, in one scrolling column.

- **Add widget** lists the widgets of every installed app. Android asks once to allow Tenseven to hold a widget, and some widgets show their own setup screen first (a weather city, say).
- **Edit** puts Up, Down and Remove over each widget. Widgets take touches themselves, so the controls only appear in Edit.
- **BlackBerry 7**: menu, then Widgets. Back returns to BlackBerry 7.
- The page can be switched off in Launcher Settings. Widget choices belong to the phone and are not in a backup.
- Widgets are drawn by their own apps, so they will not match Tenseven's look, and controls inside them are touch-only.

## Icon grid, themes and wallpapers

Launcher Settings, **Icon grid**: choose Auto or 3 to 6 columns, 1 to 3 rows, the icon size, and whether app names show under icons. The small preview above the buttons changes as you choose. Columns and names apply to the BlackBerry 7 home and tray; names also apply to the BlackBerry 10 app grid, whose columns stay fixed. (Ripe's separate "tile width" is the same choice here: the number of columns sets the width.)

**Theme** has seven looks (Default follows the phone, then Midnight, Daylight, Forest, Dusk, Amber and Slate). They work on an ordinary install and are saved with a backup. **Wallpapers** has eight pictures Tenseven draws itself, with no outside artwork: pick one, then choose **Home**, **Lock** or **Both**.

## Hub settings and the Hub peek

Launcher Settings, **Hub settings** (also the Hub's own settings button): **Clear the Hub now** forgets everything the Hub holds, **Apps in the Hub** turns individual apps off so their notifications stay out of the Hub, the peek and the lock layer, and **Show message text** decides whether the latest message may appear on the peek and the lock layer (off: names and counts only). Nothing is stored.

**Peek**: on the BlackBerry 10 home, drag in from the very left edge to slide the latest conversations over Home. Tap one to open it in its app, **Open Hub** for the full page, Back or Escape to close. Space on the Hub slot of the bottom bar does the same.

## Widgets on the home screen

On the BlackBerry 10 home, other apps' widgets can sit as full-width tiles under the Glance frames. Open **Edit Home**, find **Widgets on Home**, and choose **Add widget**; Up, Down and Remove change the order. Widget changes apply at once; Cancel does not undo them. They show after the recent-app cards, or under the Glance cards while no app has been opened. Edit Home now ends in a bar that stays on screen: **Reset**, **Cancel**, **Done**.

## Lock layer

Launcher Settings, **Lock screen** (also a Setup step), switch on **Tenseven lock layer**. When the screen wakes onto your lock screen, Tenseven shows a BlackBerry-style cover: the time, the date, an icon with a count for each app with waiting notifications, and a camera shortcut. It is only a cover: swipe up, press Enter or Space, or tap an app, and Android asks for your PIN, pattern or fingerprint before anything opens. The camera opens the secure camera without unlocking.

It needs two permissions, both explained in Settings: **Display over other apps** (so it can appear when the screen wakes) and **Notification access** (for the counts; message text only if Hub settings allow it). Some Android versions refuse to start a screen from the background; if the layer does not appear, switch it off.

## Finding things

- **All Apps privacy detail**: in All Apps, move to an app and press **Space** to open its permissions and special access (Space again closes it). The same works in Peek.
- **Appearance**: type `appearance` in Command, or scroll to the bottom of Edit Home and tap **appearance**. It sets the theme and accent; Launcher Settings, Theme has the ready-made looks.

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

Tiles and icons open whatever app your phone uses for calls, messages, mail, camera, calendar, browser, gallery and music. Tenseven has **no weather tile of its own**, because it never connects to the internet. For live weather, add a weather app's widget on the Widgets page.

## Backup

Launcher Settings, then **Backup**, saves or restores your settings as a file through Android's file picker. Letter assignments and speed dial are not included, because they hold contact names.

## Privacy

No network access, no analytics, no accounts. Optional permissions: notification access (Hub, Now Playing), usage access (recent apps), calendar (agenda), contacts (names), Do Not Disturb and system settings (sound). The accessibility service for double-tap to lock is optional and reads nothing.

## Screenshots

`tools/take-screenshots.sh` captures the setup steps, Launcher Settings and the home screens from a phone connected over adb. See [`SCREENSHOTS.md`](SCREENSHOTS.md).

## Troubleshooting

- **A permission is greyed out**: use Allow restricted settings (above).
- **Silent mode will not stick**: grant Do Not Disturb access.
- **The lock layer does not appear on wake**: use **Preview the lock layer** (Launcher Settings or Setup) to check the screen itself, confirm both permissions show as allowed, and note that Android may block a screen from starting in the background. Switch it off if it does not work on your phone.
- **Wallpaper not visible on the BlackBerry 10 home**: Launcher Settings, Wallpapers, **Show wallpaper behind the BlackBerry 10 home**.
- **Wrong home after an update**: press Home and choose Tenseven, then **Always**.
- Report problems with the device, Android version and steps on the GitHub issue tracker.
