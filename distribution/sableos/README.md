# SableOS identity overlay

TenSeven Launcher and Sable Launcher are the same source tree. The TenSeven values live in
`app/src/main/res/values/launcher_identity.xml` and the Gradle files carry no SableOS settings.

The ROM build adds this overlay on the command line:

```
./gradlew -I distribution/sableos/identity.init.gradle :app:assembleRelease
```

It sets the application ID (`org.sableos.launcher`, or `-Plauncher.applicationId=...`) and overlays the visible name, the
home icon, the Home status-dial default (off) and the double-tap-lock service labels. In-app text takes the name
from `@string/tenseven_app_name` (`appName()` in Compose), so the overlay renames every screen. Resources in the build-type source sets take priority over `main`,
so no resource is duplicated.
