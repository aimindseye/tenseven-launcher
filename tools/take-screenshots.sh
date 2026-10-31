#!/usr/bin/env bash
# Takes screenshots of Tenseven from a phone connected over adb: every Setup step, the Setup overview, Launcher
# Settings (scrolled to the end), Hub settings, Backup, the home style chooser, the lock layer, both home screens, and
# then a guided list of screens that need your hands (Hub, Widgets page, peek, Edit Home, Command, All Apps, ...).
#
#   tools/take-screenshots.sh                  # debug build, first connected phone, folder ./screenshots/<date-time>
#   tools/take-screenshots.sh -s SERIAL        # a particular phone (adb devices)
#   tools/take-screenshots.sh -o ~/Desktop/tenseven-shots
#   tools/take-screenshots.sh --no-manual      # skip the guided part
#   tools/take-screenshots.sh --release        # io.github.aimindseye.tenseven (private screens cannot be opened; see below)
#
# Needs: adb and python3 on this computer, the phone unlocked with USB debugging on, and a DEBUG build of Tenseven
# installed (the debug build lets adb open the Setup, Hub settings, Backup and lock layer screens, and lets the script
# set a few preferences through run-as). With --release only the exported screens (Launcher Settings, the home style
# chooser) and the guided part are captured.
#
# The script changes preferences on the phone (home style, onboarding done) to reach each screen. It saves the originals
# first and puts them back at the end, unless you pass --keep.
set -euo pipefail

PKG="io.github.aimindseye.tenseven.debug"
SERIAL=""
OUT=""
MANUAL=1
KEEP=0
RELEASE=0
while [ $# -gt 0 ]; do
  case "$1" in
    -s) SERIAL="$2"; shift 2 ;;
    -o) OUT="$2"; shift 2 ;;
    --no-manual) MANUAL=0; shift ;;
    --keep) KEEP=1; shift ;;
    --release) PKG="io.github.aimindseye.tenseven"; RELEASE=1; shift ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
done

command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools." >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 not found." >&2; exit 1; }
ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "No phone found. Run 'adb devices' and pass -s SERIAL if more than one." >&2; exit 1; }
"${ADB[@]}" shell pm path "$PKG" >/dev/null 2>&1 || { echo "$PKG is not installed on the phone." >&2; exit 1; }

[ -n "$OUT" ] || OUT="screenshots/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
N=0
PREFS="shared_prefs/sable_start_state.xml"
BACKUP="$OUT/.prefs-before.xml"
ACT="io.github.aimindseye.tenseven"

adbs() { "${ADB[@]}" shell "$@"; }

SIZE=$("${ADB[@]}" shell wm size | grep -Eo '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}
H=${SIZE#*x}
X=$((W / 2))
TOP=$((H * 3 / 10))
LOW=$((H * 8 / 10))
MID=$((H / 2))
LEFT=$((W / 10))
RIGHT=$((W * 9 / 10))

hash_screen() {
  if command -v md5 >/dev/null; then "${ADB[@]}" exec-out screencap -p | md5; else "${ADB[@]}" exec-out screencap -p | md5sum; fi
}

shot() { # shot <name>
  N=$((N + 1))
  local file; file="$OUT/$(printf '%02d' "$N")-$1.png"
  "${ADB[@]}" exec-out screencap -p > "$file"
  echo "  $file"
}

wake() {
  adbs input keyevent KEYCODE_WAKEUP
  adbs wm dismiss-keyguard 2>/dev/null || true
  sleep 0.5
}

start() { # start <activity class> [extra args...]
  local cls="$1"; shift
  adbs am start -W -n "$PKG/$ACT.$cls" "$@" >/dev/null
  sleep 1.2
}

home() { adbs input keyevent KEYCODE_HOME; sleep 1.2; }

# Scrolls the current screen down and captures each step until the picture stops changing.
scroll_shots() { # scroll_shots <name>
  local name="$1" i prev="" cur
  for i in $(seq 1 14); do
    cur="$(hash_screen)"
    [ "$cur" = "$prev" ] && break
    shot "$name-$(printf '%02d' "$i")"
    prev="$cur"
    adbs input swipe "$X" "$LOW" "$X" "$TOP" 400
    sleep 0.6
  done
}

# --- preferences through run-as (debug builds only) ---------------------------------------------------------------
HAVE_PREFS=0
if [ "$RELEASE" = 0 ] && adbs run-as "$PKG" true >/dev/null 2>&1; then HAVE_PREFS=1; fi

read_prefs() { adbs run-as "$PKG" cat "$PREFS" 2>/dev/null || true; }

write_prefs() { # reads the new XML on stdin
  "${ADB[@]}" shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > $PREFS'"
}

EDIT_PREF=$(cat <<'PY'
import re, sys
kind, key, value = sys.argv[1:4]
xml = sys.stdin.read() or "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>\n"
xml = re.sub(r'\s*<(string|boolean|int)\s+name="%s"[^>]*?(/>|>.*?</string>)' % re.escape(key), "", xml, flags=re.S)
if kind == "string":
    entry = '<string name="%s">%s</string>' % (key, value)
else:
    entry = '<boolean name="%s" value="%s" />' % (key, value)
xml = xml.replace("</map>", "    " + entry + "\n</map>")
sys.stdout.write(xml)
PY
)

set_pref() { # set_pref <string|boolean> <key> <value>
  [ "$HAVE_PREFS" = 1 ] || return 0
  adbs am force-stop "$PKG"
  read_prefs | python3 -I -c "$EDIT_PREF" "$1" "$2" "$3" | write_prefs
}

restore_prefs() {
  [ "$HAVE_PREFS" = 1 ] && [ "$KEEP" = 0 ] && [ -s "$BACKUP" ] || return 0
  adbs am force-stop "$PKG"
  write_prefs < "$BACKUP"
  echo "Preferences put back."
}

if [ "$HAVE_PREFS" = 1 ]; then
  read_prefs > "$BACKUP"
  trap restore_prefs EXIT
else
  echo "Note: run-as is not available (release build or a locked phone), so the script cannot set preferences."
fi

echo "Saving screenshots to $OUT"
wake

# --- Setup: each step on its own, then the overview -----------------------------------------------------------------
if [ "$RELEASE" = 0 ]; then
  echo "Setup steps"
  set_pref boolean onboarding_done false
  for step in HOME STYLE CONTACTS PHOTOS NOTIFICATIONS USAGE LOOK GRID WIDGETS HUB LOCK EXTRAS; do
    start OnboardingActivity --es io.github.aimindseye.tenseven.extra.SETUP_STEP "$step"
    shot "setup-$(echo "$step" | tr '[:upper:]' '[:lower:]')"
    case "$step" in LOOK|GRID|LOCK|EXTRAS) adbs input swipe "$X" "$LOW" "$X" "$TOP" 400; sleep 0.6; shot "setup-$(echo "$step" | tr '[:upper:]' '[:lower:]')-more" ;; esac
  done
  echo "Setup overview (the screen Launcher Settings, then Setup, opens)"
  set_pref boolean onboarding_done true
  start OnboardingActivity
  scroll_shots setup-overview
fi

# --- Launcher Settings and the other Tenseven screens ---------------------------------------------------------------
echo "Launcher Settings"
start LauncherSettingsActivity
scroll_shots launcher-settings

if [ "$RELEASE" = 0 ]; then
  echo "Hub settings"
  start HubSettingsActivity
  scroll_shots hub-settings
  echo "Backup"
  start BackupActivity
  shot backup
  echo "Lock layer (a preview, so it does not ask for the PIN)"
  start LockLayerActivity
  shot lock-layer
  adbs input keyevent KEYCODE_ENTER; sleep 0.6
fi

echo "Home style chooser"
start HomeStyleActivity
shot home-style

# --- The two home screens ------------------------------------------------------------------------------------------
for style in bb10 bb7; do
  if [ "$HAVE_PREFS" = 1 ]; then
    echo "Home screen: $style"
    set_pref string home_style "$style"
  else
    echo "Home screen: switch to $style in Launcher Settings, then press Enter here."
    read -r _
  fi
  home
  shot "home-$style"
  # a second page to the right, then back (BB10: Hub; BB7: Hub or the apps)
  adbs input swipe "$LEFT" "$MID" "$RIGHT" "$MID" 300; sleep 1; shot "home-$style-swipe-right"
  home
  adbs input swipe "$RIGHT" "$MID" "$LEFT" "$MID" 300; sleep 1; shot "home-$style-swipe-left"
  home
done

# --- Guided part: screens that need keys and taps ------------------------------------------------------------------
if [ "$MANUAL" = 1 ]; then
  echo
  echo "Guided screens. For each one, get the phone to that screen, then press Enter here (or type s then Enter to skip)."
  guided() { # guided <name> <what to do>
    printf '\n[%s]\n  %s\n  Enter = capture, s = skip: ' "$1" "$2"
    local answer; read -r answer
    [ "$answer" = s ] || shot "$1"
  }
  [ "$HAVE_PREFS" = 1 ] && set_pref string home_style bb10 && home
  guided hub-page "BlackBerry 10: swipe right to the Hub."
  guided widgets-page "BlackBerry 10: the Widgets page (swipe right past the Hub), with a widget added if you have one."
  guided hub-peek "BlackBerry 10 Home: drag in from the very left edge so the peek panel is open."
  guided active-frames "BlackBerry 10 Home with some apps open (Active Frames)."
  guided home-widget-tile "BlackBerry 10 Home with a widget added under Widgets on Home."
  guided edit-home "BlackBerry 10: Edit Home (gear, or Command: edit home). The Reset / Cancel / Done bar stays at the bottom."
  guided edit-home-widgets "Edit Home scrolled to Widgets on Home."
  guided command "Command (Alt+Space, or swipe down on the BlackBerry 10 home), with a few letters typed."
  guided all-apps "All Apps (A to Z)."
  guided app-privacy-row "All Apps (BlackBerry 10 bottom bar, All Apps), move to an app with the trackpad or arrows and press Space: its privacy detail (permissions and special access) opens."
  guided appearance "Appearance: open Command and type  appearance  then Enter, or open Edit Home and scroll to the very bottom, then tap the  appearance  link."
  [ "$HAVE_PREFS" = 1 ] && set_pref string home_style bb7 && home
  guided bb7-menu "BlackBerry 7: open the menu (Alt+Enter or the three dots)."
  guided bb7-desktop-icons "BlackBerry 7 with icons added to the home screen (Add to home screen)."
  guided bb7-tray-all "BlackBerry 7 with the All panel open."
  guided sound-profiles "Sound Profiles, if switched on in Extras (tap the speaker icon)."
  guided extras "An extra app, for example Calc Plus or Notes."
  echo
  echo "Last one needs the screen off. In Launcher Settings, switch on the lock layer and allow both permissions first."
  printf 'Capture the lock layer on a real wake now? (y/N): '
  read -r answer
  if [ "$answer" = y ]; then
    adbs input keyevent KEYCODE_SLEEP; sleep 2
    adbs input keyevent KEYCODE_WAKEUP; sleep 2.5
    shot lock-layer-on-wake
    echo "(The phone is on its lock screen; unlock it yourself.)"
  fi
fi

echo
echo "Done: $N screenshots in $OUT"
