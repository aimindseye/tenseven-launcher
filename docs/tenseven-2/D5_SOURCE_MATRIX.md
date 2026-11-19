# D5 source matrix and safety boundaries

## Five existing-file modifications

| Path | Change | Why |
|---|---|---|
| `LauncherSettingsActivity.kt` | UI label `Text` -> `Icons`, description and review summary | User approved Settings corrections; stable underlying enum unchanged |
| `model/HomeStatusSize.kt` | Neutral Default description | Remove legacy milestone reference |
| `model/HomeDisplayPolicy.kt` | `DEFAULT_DIALS = ALL_DIALS` | Storage on by default when no preference exists, never changing stored selection |
| `model/ExtraApps.kt` | Two optional app descriptors | Existing app control (Minimum/Full/Custom) automatically manages visibility |
| `AndroidManifest.xml` | Disabled launchable D5 activities, internal floating activity | Safe optional Full APK integration |

## Seven added source/resource files

| Path | Purpose |
|---|---|
| `features/calendar/D5CalendarRepository.kt` | Read-only Android CalendarProvider access and filtering |
| `features/calendar/TenSevenCalendarActivity.kt` | Month, busy indicators, day and week schedule, sources, event actions |
| `features/phone/TenSevenPhoneActivity.kt` | Phone tabs, keypad, contacts, floating translucent activity, ACTION_DIAL |
| `model/D5CalendarBusy.kt` | JVM-testable merged busy time, calendar grid |
| `model/D5PhoneDialPolicy.kt` | JVM-testable keypad normalization |
| `res/values/tenseven_d5.xml` | Optional app names |
| `src/test/.../model/D5PoliciesTest.kt` | Date and phone input unit tests |

## Strict boundaries

- Do not edit Active Frames, `SableStartScreen` swipe pager, Hub notification listener, lock-screen implementation, signing or package ID.
- Do not request Android Phone default-dialer role, CALL_PHONE, READ_CALL_LOG or WRITE_CALL_LOG; preserve current Phone app.
- Do not add WRITE_CALENDAR. New event goes through ACTION_INSERT in the user's chosen calendar app.
- READ_CALENDAR and READ_CONTACTS already exist in the base manifest; both are runtime opt-in.
- No source of SableOS Calendar/Phone was copied; design and public Android API behavior were reviewed for compatibility.

## Exact testing scope

Installer and synthetic source fixture, JVM-only logic, XML parse/checksum. **No Android compilation or device execution was possible in the artifact builder.** On Mac, compile and fix any remaining integration errors before APK install.
