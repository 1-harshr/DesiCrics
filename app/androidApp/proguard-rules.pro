# R8 rules for the release build. Libraries ship their own consumer rules (kotlinx.serialization, Ktor,
# Koin, Firebase, Compose), so this file only holds what is specific to this app.

# Keep line numbers so Crashlytics stack traces point at real lines once deobfuscated, and hide the
# original file names in the shipped APK.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
