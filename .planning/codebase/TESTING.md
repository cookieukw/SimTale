# Testing

## Automated Tests

- **Current State:** No automated tests (JUnit/TestNG) found in the `src/test` directory.
- **Infrastructure:** Gradle test task is available but currently unused.

## Manual Verification

- **Deployment:** Manual testing via the `deploy` Gradle task, which copies the plugin JAR to the Hytale `UserData/Mods` directory.
- **Server Logging:** Verification of mod loading and event handling via Hytale server console outputs.
