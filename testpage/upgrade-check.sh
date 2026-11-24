#!/bin/sh
set -eu
if [ -d old-apk ]; then
  adb install old-apk/*x86_64-emulator.apk
  adb shell run-as app.nova.browser.preview sh -c 'echo nova-upgrade-retained > files-upgrade-marker'
  adb shell dumpsys package app.nova.browser.preview | grep version > shots/upgrade-before.txt
fi
adb install -r app/build/outputs/apk/x64/debug/app-x64-debug.apk
if [ -d old-apk ]; then
  adb shell run-as app.nova.browser.preview cat files-upgrade-marker | grep nova-upgrade-retained
  adb shell dumpsys package app.nova.browser.preview | grep version > shots/upgrade-after.txt
  printf 'In-place install accepted and private data marker retained.\n' > shots/upgrade-result.txt
fi
