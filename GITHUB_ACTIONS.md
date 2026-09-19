# GitHub Actions

The workflow at `.github/workflows/build.yml` builds the Android debug APK and uploads it as an artifact.

Important: the repository must include a Gradle wrapper (`gradlew`, `gradlew.bat`, and `gradle/wrapper/*`).
Generate it once locally with a compatible Gradle installation:
`gradle wrapper --gradle-version 8.9`

Then commit the generated wrapper files.

After pushing to GitHub:
Actions -> Build Geyser Mobile -> Run workflow.

The APK will appear under the workflow run's Artifacts section.
