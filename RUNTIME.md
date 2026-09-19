# Java 21 runtime integration

Geyser Standalone requires Java 21+. The build workflow downloads an Android-compatible Java 21 runtime produced for the PojavLauncher ecosystem, extracts the universal runtime plus ARM64 native binaries, and bundles it into the APK assets.

At first start, Geyser Mobile copies that runtime from APK assets into app-private storage and launches Geyser with it. No Termux installation is required.

The current build targets ARM64 for the bundled runtime. The Android UI and Geyser service remain native Android components.

Official Geyser docs: https://geysermc.org/wiki/geyser/setup/self/standalone/
