#!/usr/bin/env sh
# Copies layout sources into androidTest assets so LayoutRenderTest can re-apply tools:* sample data.
# Usage (from android/): sh scripts/sync-preview-layouts.sh && ./gradlew :app:connectedMockDebugAndroidTest
set -e
dir="app/src/androidTest/assets/layouts"
mkdir -p "$dir"
rm -f "$dir"/*.xml
cp app/src/main/res/layout/*.xml "$dir"/
