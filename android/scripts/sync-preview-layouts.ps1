# Run from android/: .\scripts\sync-preview-layouts.ps1
# Then: .\gradlew.bat :app:connectedMockDebugAndroidTest
$ErrorActionPreference = 'Stop'
$androidRoot = Split-Path -Parent $PSScriptRoot
$previewAssets = Join-Path $androidRoot 'app/src/androidTest/assets/layouts'
New-Item -ItemType Directory -Path $previewAssets -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $androidRoot 'app/src/main/res/layout') -Filter '*.xml' -File |
    Copy-Item -Destination $previewAssets -Force
