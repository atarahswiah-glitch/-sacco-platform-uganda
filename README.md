# SACCO Platform Uganda — Native Android foundation

This is a fresh native Android application foundation using Kotlin and Jetpack Compose. It is not a web wrapper and does not replace any earlier project folder.

## Included in this foundation
- Native account ID + PIN sign-in screen calling the Supabase `sacco-login` Edge Function.
- Role and SACCO context loaded from `sacco-dashboard-bootstrap` after sign-in.
- Role-specific dashboard cards and the agreed five bottom destinations: Home, Accounts, Policies, Alerts, Settings.
- Separate Workflow & Operations and Financial Display & Privacy settings, with authenticated REST updates.
- In-app notifications screen backed by the tenant-scoped notifications table.
- A GitHub Actions workflow to build a debug APK and publish it as an artifact.

## Backend settings
The app uses the Supabase project URL and **publishable** API key in `MainActivity.kt`. A publishable key is designed for client use; never put a service-role or secret key in the Android app.

## Build
Use Android Studio or run `gradle assembleDebug` with JDK 17, Gradle 8.9, Android SDK platform 35, and build-tools 35.0.0. This starter does not include the Gradle wrapper JAR. GitHub Actions installs the required build tooling automatically.

## Important status
This source package has not yet been compiled on a real Android SDK. Authentication end-to-end testing is still pending: the current Supabase PIN/password policy may reject numeric-only PINs, and no real member account has been provisioned. Do not distribute this as a production APK until authentication, role authorization, account recovery, offline synchronization, ledger workflows, and end-to-end tests are complete.


## First-run Platform Owner registration

- On first launch, the app asks for the system-issued Platform Owner Account ID and checks it against the active Supabase account registry before opening the next screen.
- The reserved Platform Owner account completes first-time setup by choosing and confirming a private 8–12 digit PIN; no temporary PIN or SMS delivery is assumed.
- After activation, the same Account ID follows the normal sign-in path on later launches.
- Other SACCO accounts continue to require their own issued temporary PIN for first activation.
- The Android app and these Edge Function sources target the single configured Supabase project for this branch.


## Platform Owner activation boundaries

- The Platform Owner may activate a newly registered SACCO tenant together with its first chairperson account.
- The chairperson receives a one-time Account ID and temporary PIN and must set a private PIN before using the chairperson dashboard.
- The Platform Owner cannot provision or activate ordinary member accounts. After the tenant is active, the chairperson or an authorised SACCO administrator provisions all other SACCO accounts.
