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
adb exec-out screencap -p > "$SCREENSHOT_DIR/01-launch-home.png"
adb shell uiautomator dump /sdcard/window-launch.xml
adb pull /sdcard/window-launch.xml "$SCREENSHOT_DIR/window-launch.xml"

set +e
python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

def text_of(node):
    return node.attrib.get("text", "") or node.attrib.get("content-desc", "")

root = ET.parse(f"{SCREENSHOT_DIR}/window-launch.xml").getroot()
got_it = next((node for node in root.iter("node") if text_of(node).upper() == "GOT IT"), None)
if got_it is not None:
    x, y = center(got_it.attrib["bounds"])
    with open(f"{SCREENSHOT_DIR}/onboarding-got-it-center.txt", "w") as f:
        f.write(f"{x} {y}\n")
    raise SystemExit(2)
add = next(node for node in root.iter("node") if text_of(node) in {"+ Add", "Add task"})
x, y = center(add.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/add-affordance-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY
launch_lookup_status="$?"
set -e
case "$launch_lookup_status" in
  0) ;;
  2)
    read got_it_x got_it_y < "$SCREENSHOT_DIR/onboarding-got-it-center.txt"
    adb shell input tap "$got_it_x" "$got_it_y"
    sleep 1
    adb exec-out screencap -p > "$SCREENSHOT_DIR/01b-launch-home-after-onboarding.png"
    adb shell uiautomator dump /sdcard/window-launch.xml
    adb pull /sdcard/window-launch.xml "$SCREENSHOT_DIR/window-launch.xml"
    python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

def text_of(node):
    return node.attrib.get("text", "") or node.attrib.get("content-desc", "")

root = ET.parse(f"{SCREENSHOT_DIR}/window-launch.xml").getroot()
add = next(node for node in root.iter("node") if text_of(node) in {"+ Add", "Add task"})
x, y = center(add.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/add-affordance-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY
    ;;
  *) exit 1 ;;
esac

read add_x add_y < "$SCREENSHOT_DIR/add-affordance-center.txt"
adb shell input tap "$add_x" "$add_y"
sleep 2
adb shell input keyevent KEYCODE_BACK || true
sleep 1
adb exec-out screencap -p > "$SCREENSHOT_DIR/02-add-panel.png"
adb shell uiautomator dump /sdcard/window-add-panel.xml
adb pull /sdcard/window-add-panel.xml "$SCREENSHOT_DIR/window-add-panel.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-add-panel.xml").getroot()
edit = next(node for node in root.iter("node") if node.attrib.get("class") == "android.widget.EditText")
x, y = center(edit.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/title-input-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read title_x title_y < "$SCREENSHOT_DIR/title-input-center.txt"
adb shell input tap "$title_x" "$title_y"
sleep 1
adb shell input text CI_task
sleep 1
adb exec-out screencap -p > "$SCREENSHOT_DIR/02b-keyboard-open.png"
adb shell uiautomator dump /sdcard/window-keyboard-open.xml
adb pull /sdcard/window-keyboard-open.xml "$SCREENSHOT_DIR/window-keyboard-open.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def bounds(node):
    return list(map(int, re.findall(r"\d+", node.attrib["bounds"])))

root = ET.parse(f"{SCREENSHOT_DIR}/window-keyboard-open.xml").getroot()
save = next(node for node in root.iter("node") if node.attrib.get("text") in {"Add task", "Save"})
edit = next(node for node in root.iter("node") if node.attrib.get("class") == "android.widget.EditText")
save_bounds = bounds(save)
edit_bounds = bounds(edit)

if save.attrib.get("visible-to-user") == "false":
    raise AssertionError("Add task action is not visible while the keyboard is open")
if save_bounds[3] > 900:
    raise AssertionError(f"Add task action is too low with keyboard open: {save_bounds}")
if edit_bounds[3] > save_bounds[1]:
    raise AssertionError(f"Task input overlaps action row: input={edit_bounds}, action={save_bounds}")
PY

adb shell input keyevent KEYCODE_BACK || true
sleep 1
adb shell uiautomator dump /sdcard/window-before-save.xml
adb pull /sdcard/window-before-save.xml "$SCREENSHOT_DIR/window-before-save.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-before-save.xml").getroot()
save = next(node for node in root.iter("node") if node.attrib.get("text") in {"Add task", "Update task"})
x, y = center(save.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/save-button-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read save_x save_y < "$SCREENSHOT_DIR/save-button-center.txt"
adb shell input tap "$save_x" "$save_y"
sleep 2
adb exec-out screencap -p > "$SCREENSHOT_DIR/03-after-add.png"
adb shell uiautomator dump /sdcard/window-after-add.xml
adb pull /sdcard/window-after-add.xml "$SCREENSHOT_DIR/window-after-add.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-after-add.xml").getroot()
tasks = [node for node in root.iter("node")
         if node.attrib.get("text") in {"CI task", "CI_task"}]
task = max(tasks, key=lambda node: center(node.attrib["bounds"])[1])
x, y = center(task.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/task-row-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read task_x task_y < "$SCREENSHOT_DIR/task-row-center.txt"
adb shell input tap "$task_x" "$task_y"
sleep 1
adb exec-out screencap -p > "$SCREENSHOT_DIR/03b-after-task-tap.png"
adb shell uiautomator dump /sdcard/window-after-task-tap.xml
adb pull /sdcard/window-after-task-tap.xml "$SCREENSHOT_DIR/window-after-task-tap.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-after-task-tap.xml").getroot()
descriptions = {node.attrib.get("content-desc", "") for node in root.iter("node")}
if not descriptions.intersection({
        "Expanded details for CI task", "Expanded details for CI_task"}):
    raise AssertionError("Tapping a task did not expand its inline details")
texts = {node.attrib.get("text", "") for node in root.iter("node")}
if "Save" in texts:
    raise AssertionError("Tapping a task opened the edit sheet instead of inline details")
PY

adb shell input swipe "$task_x" "$task_y" "$task_x" "$task_y" 700
sleep 1
adb exec-out screencap -p > "$SCREENSHOT_DIR/03c-after-task-long-press.png"
adb shell uiautomator dump /sdcard/window-after-task-long-press.xml
adb pull /sdcard/window-after-task-long-press.xml "$SCREENSHOT_DIR/window-after-task-long-press.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-after-task-long-press.xml").getroot()
texts = {node.attrib.get("text", "") for node in root.iter("node")}
if "Save" not in texts:
    raise AssertionError("Long-pressing a task did not open its edit sheet")
cancel = next(node for node in root.iter("node") if node.attrib.get("text") == "Cancel")
x, y = center(cancel.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/edit-cancel-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read cancel_x cancel_y < "$SCREENSHOT_DIR/edit-cancel-center.txt"
adb shell input tap "$cancel_x" "$cancel_y"
sleep 1
adb shell uiautomator dump /sdcard/window-after-edit-close.xml
adb pull /sdcard/window-after-edit-close.xml "$SCREENSHOT_DIR/window-after-edit-close.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-after-edit-close.xml").getroot()
checkbox = next(node for node in root.iter("node") if node.attrib.get("class") == "android.widget.CheckBox")
x, y = center(checkbox.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/checkbox-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read check_x check_y < "$SCREENSHOT_DIR/checkbox-center.txt"
adb shell input tap "$check_x" "$check_y"
sleep 1
adb exec-out screencap -p > "$SCREENSHOT_DIR/04-after-checkbox-complete.png"
adb shell uiautomator dump /sdcard/window-after-checkbox-complete.xml
adb pull /sdcard/window-after-checkbox-complete.xml "$SCREENSHOT_DIR/window-after-checkbox-complete.xml"

adb shell settings put system font_scale 1.0 || true
adb shell wm size reset || true
adb shell wm density reset || true
