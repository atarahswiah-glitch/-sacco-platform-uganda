# Circle Platform Uganda Android

A clean, native Android starter for the Circle Platform Uganda / SACCO Platform Uganda project.

## Current status
This is a starter build, not a complete SACCO system. It contains a native Android screen and a GitHub Actions workflow that builds a debug APK. Authentication, tenant isolation, Supabase integration, financial workflows, offline sync, and production security still need to be implemented and tested.

## Build
GitHub Actions runs on pushes and can also be started manually from the Actions tab. The workflow uploads `app-debug.apk` as a downloadable artifact.

## Security
Never place a Supabase service-role key in the Android app or repository. The Android app must use the public anon/publishable key with correctly configured Row Level Security (RLS). Do not store real member data in this starter.
