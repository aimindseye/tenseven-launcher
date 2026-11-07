# D0 — Titan 2 acceptance and evidence plan

1. Start from main commit 50f8b1a6a5e22dfd9ac6189fc1301a421984808d on a separate review branch.
2. Run Gradle unit tests, release build and lint; verify signed APK against the registered certificate.
3. Install with adb -s TITAN20000006073 install -r; preserve all existing launcher data.
4. In Hub Settings enable automatic sources, and collect Copy D0 notification audit after multiple WhatsApp chats, emails and silent notifications.
5. Read the copied per-package observed/visible/filter/parse/group/silent counts; do not share message content or raw dumpsys output.
6. Confirm Active Frames, Hub, Peek and lock screen remain functional on Titan 2.

Observed count is what Android delivered to the listener in a snapshot; no history or complete shade comparison is implied. D0 acceptance does not imply D2 notification defects have been fixed. 
