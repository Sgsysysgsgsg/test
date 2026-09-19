# Geyser Mobile 1.1.0

Native Android app for running Geyser Standalone locally on Android without Termux.

Features:
- Geyser Standalone download and launch
- Bundled Android-compatible Java 21 runtime (ARM64)
- Java server IP/port configuration
- Bedrock UDP port configuration
- Online / Offline / Floodgate authentication
- Floodgate `.pem` key import (including names such as `nlk.pem`)
- Foreground service for the Geyser process

Geyser Standalone requires Java 21 or higher. GeyserMC documents Android/Termux execution and the Java 21 requirement here:
https://geysermc.org/wiki/geyser/setup/self/standalone/

The CI downloads the Android JRE 21 from the PojavLauncher runtime build project and packages it into the APK. PojavLauncher documents its pre-built Android JREs and lists OpenJDK 21 support for ARM64 and other architectures.


## 1.2.4
Uses Android system linker (`/system/bin/linker64` or `linker`) to launch the bundled Java runtime from app-private storage on Android 10+ where direct `execve` can return Permission denied.


## Geyser Mobile 1.3.0

1.3.0 replaces direct execution of `files/runtime/bin/java` with an Android native JLI launcher. The Java 21 runtime is still bundled as an Android-compatible ARM64 JRE archive, but Java is started through `libjli.so` from a native library extracted by Android. The Geyser service runs in a dedicated `:geyser` process so the Stop button can terminate the JVM cleanly.
\n\n## 1.3.1\n\nFixes the Android-native Java launcher library path for the Pojav Android JRE 21 layout. `libjli.so` is loaded from `runtime/lib/libjli.so` and `libjvm.so` from `runtime/lib/server/libjvm.so`.\n