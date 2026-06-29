#!/usr/bin/env sh
set -eu

SCREENSHOT_DIR="app/build/verification-screenshots"
mkdir -p "$SCREENSHOT_DIR"

adb wait-for-device
until adb shell service check package | grep -q found; do
  sleep 5
done
until adb shell service check settings | grep -q found; do
  sleep 5
done

install_ok=0
for attempt in 1 2 3; do
  if adb install -r app/build/outputs/apk/debug/app-debug.apk; then
    install_ok=1
    break
  fi
  sleep 10
done
test "$install_ok" = 1

adb shell settings put system font_scale 1.3
adb shell wm size 720x1280
adb shell wm density 320
adb shell am start -W -n com.tj90.prioritytodo/.MainActivity
sleep 3
adb exec-out screencap -p > "$SCREENSHOT_DIR/01-launch-large-font.png"
adb shell uiautomator dump /sdcard/window-launch.xml
adb pull /sdcard/window-launch.xml "$SCREENSHOT_DIR/window-launch.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-launch.xml").getroot()
edit = next(node for node in root.iter("node") if node.attrib.get("class") == "android.widget.EditText")
x, y = center(edit.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/title-input-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read title_x title_y < "$SCREENSHOT_DIR/title-input-center.txt"
adb shell input tap "$title_x" "$title_y"
sleep 2
adb exec-out screencap -p > "$SCREENSHOT_DIR/02-keyboard-compact-height.png"
adb shell uiautomator dump /sdcard/window-keyboard.xml
adb pull /sdcard/window-keyboard.xml "$SCREENSHOT_DIR/window-keyboard.xml"
adb shell input text CI_task

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-keyboard.xml").getroot()
add = next(node for node in root.iter("node") if node.attrib.get("text") == "Add task")
x, y = center(add.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/add-button-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read add_x add_y < "$SCREENSHOT_DIR/add-button-center.txt"
adb shell input tap "$add_x" "$add_y"
sleep 1
adb exec-out screencap -p > "$SCREENSHOT_DIR/03-after-add.png"

adb shell settings put system font_scale 1.0 || true
adb shell wm size reset || true
adb shell wm density reset || true
