# Screenshots

`tools/take-screenshots.sh` captures Tenseven from a phone connected over adb. It is meant for the README, the user
guide and for checking every screen after a change.

## What you need

- adb (Android platform-tools) and python3 on your computer
- the phone unlocked, USB debugging on, and a **debug build** of Tenseven installed (`io.github.aimindseye.tenseven.debug`)

The debug build opens Tenseven's private screens (Setup, Hub settings, Backup, the lock layer) to adb, and lets the
script set a few preferences through `run-as`. Release builds keep those screens private, so with `--release` only
Launcher Settings, the home style chooser and the guided part are captured.

## Run it

```bash
tools/take-screenshots.sh                       # first connected phone, ./screenshots/<date-time>/
tools/take-screenshots.sh -s TITAN20000006073   # a particular phone (adb devices)
tools/take-screenshots.sh -o ~/Desktop/shots    # choose the folder
tools/take-screenshots.sh --no-manual           # only the automatic screens
```

## What it captures

Automatic (no touching the phone):

| Group | Screens |
|---|---|
| Setup steps | home app, home screen style, contacts, photo counts, notification access, usage access, theme and wallpaper, icon grid, widgets, Hub, lock screen, extra apps (the longer ones also scrolled) |
| Setup overview | the screen from Launcher Settings, then Setup, scrolled to the end |
| Launcher Settings | scrolled from the top until the picture stops changing |
| Other screens | Hub settings (scrolled), Backup, the lock layer (a preview), the home style chooser |
| Home screens | BlackBerry 10 and BlackBerry 7, each with a swipe right and a swipe left |

Guided (the script names a screen, you get the phone there, press Enter): the Hub, Widgets page, Hub peek, Active
Frames, a widget tile, Edit Home with its bottom bar, Command, All Apps with a privacy row, Appearance, the BlackBerry 7
menu, home icons and All panel, Sound Profiles and an extra app. Type `s` to skip one. The last prompt wakes the
screen to capture the lock layer on a real lock screen (switch it on and allow both permissions first).

## While it runs

- **Automatic part (about the first 50 shots, roughly 5 minutes):** do nothing. Leave the phone unlocked, on the cable
  and awake, and do not touch it, since a touch lands on whatever screen is showing.
- **Guided part:** the script prints the screen it wants, you move the phone to that screen, then press Enter in the
  terminal. Type `s` to skip a screen.
- **If something looks wrong,** just run it again with a new `-o` folder. It puts your preferences back each time, so
  running it twice is safe.

Files are numbered in the order taken (`01-setup-home.png`, `02-setup-style.png`, ...).

## What it changes on the phone

To reach each screen it sets two preferences (`onboarding_done`, `home_style`) in the debug app's private storage and
stops the app. It saves the originals first and puts them back when it finishes, unless you pass `--keep`. It never
touches the release app, contacts, or anything outside Tenseven.

## If something is off

- `run-as` not allowed: the app is not a debug build, or the phone blocks it. The script says so, skips the preference
  changes and asks you to switch the home style yourself.
- A scrolled shot repeats: the screen did not move (an overlay is open). Close it and run again.
- Swipes land in the wrong place: the script reads the screen size from `adb shell wm size`; on foldables or with a
  changed display size, capture those screens in the guided part instead.
