# GitHub build — no Android Studio required

1. Create a GitHub repository.
2. Upload the contents of this project.
3. Push to `main`/`master`.
4. Open **Actions** and select **Build Geyser Mobile**.
5. The APK is uploaded under **Artifacts**.

For automatic GitHub Releases:
- Create a tag such as `v1.0.0`
- Push the tag
- `Release Geyser Mobile` builds `assembleRelease` and attaches the APK to the GitHub Release.

The workflows install Gradle and use GitHub-hosted Ubuntu runners, so Android Studio is not required.

Note: this project still needs an Android-compatible Java 21 runtime packaged/downloaded at runtime before Geyser Standalone can run without Termux. The Android app build itself uses JDK 17 for the Gradle/Android build.
