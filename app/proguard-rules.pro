# R8 rules for the release build (isMinifyEnabled = true in app/build.gradle.kts).
#
# Nothing needs keeping beyond what the Android Gradle plugin and the libraries already declare:
#  - Activities and other manifest components are kept automatically from AndroidManifest.xml.
#  - The reflection in platform/ (PermissionChangeSignal, UserContexts, UserHandles, RecentTasksRepository)
#    looks up methods on Android framework classes by name. R8 never renames framework classes, so those
#    lookups survive obfuscation. If reflection is ever added on this app's own classes, add -keep rules here.

# Keep line numbers so a stack trace can be de-obfuscated with the mapping file in
# app/build/outputs/mapping/release/ (keep that file for every release you ship).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
