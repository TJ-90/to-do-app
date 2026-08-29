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
list_pill = next(node for node in root.iter("node")
                 if node.attrib.get("content-desc", "").startswith("List: No list"))
x, y = center(list_pill.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/initial-list-pill-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read initial_list_x initial_list_y < "$SCREENSHOT_DIR/initial-list-pill-center.txt"
adb shell input tap "$initial_list_x" "$initial_list_y"
sleep 1
adb shell uiautomator dump /sdcard/window-initial-list-picker.xml
adb pull /sdcard/window-initial-list-picker.xml "$SCREENSHOT_DIR/window-initial-list-picker.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-initial-list-picker.xml").getroot()
new_list = next(node for node in root.iter("node") if node.attrib.get("text") == "＋ New list")
x, y = center(new_list.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/new-list-choice-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read new_list_x new_list_y < "$SCREENSHOT_DIR/new-list-choice-center.txt"
adb shell input tap "$new_list_x" "$new_list_y"
sleep 1
adb shell uiautomator dump /sdcard/window-create-list.xml
adb pull /sdcard/window-create-list.xml "$SCREENSHOT_DIR/window-create-list.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-create-list.xml").getroot()
edit = next(node for node in root.iter("node")
            if node.attrib.get("class") == "android.widget.EditText")
x, y = center(edit.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/new-list-name-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read new_list_name_x new_list_name_y < "$SCREENSHOT_DIR/new-list-name-center.txt"
adb shell input tap "$new_list_name_x" "$new_list_name_y"
adb shell input text Work
adb shell input keyevent KEYCODE_BACK || true
sleep 1
adb shell uiautomator dump /sdcard/window-create-list-filled.xml
adb pull /sdcard/window-create-list-filled.xml "$SCREENSHOT_DIR/window-create-list-filled.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-create-list-filled.xml").getroot()
create = next(node for node in root.iter("node") if node.attrib.get("text") == "CREATE")
x, y = center(create.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/create-list-button-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read create_list_x create_list_y < "$SCREENSHOT_DIR/create-list-button-center.txt"
adb shell input tap "$create_list_x" "$create_list_y"
sleep 2
if adb shell dumpsys input_method | grep -q 'mInputShown=true'; then
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
fi
adb shell uiautomator dump /sdcard/window-after-list-create.xml
adb pull /sdcard/window-after-list-create.xml "$SCREENSHOT_DIR/window-after-list-create.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-after-list-create.xml").getroot()
if not any(node.attrib.get("content-desc", "").startswith("List: Work")
           for node in root.iter("node")):
    raise AssertionError("New list was not assigned through the sheet picker")
edit = next(node for node in root.iter("node")
            if node.attrib.get("class") == "android.widget.EditText")
x, y = center(edit.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/title-input-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read title_x title_y < "$SCREENSHOT_DIR/title-input-center.txt"
adb shell input tap "$title_x" "$title_y"
sleep 1
adb shell input text CI_task_with_a_long_title_that_should_wrap_fully
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
edits = [node for node in root.iter("node")
         if node.attrib.get("class") == "android.widget.EditText"]
if len(edits) != 1:
    raise AssertionError(f"Collapsed add panel should expose one text field, found {len(edits)}")
edit = edits[0]
add_details = next(node for node in root.iter("node")
                   if node.attrib.get("content-desc") == "Add task details")
if add_details.attrib.get("visible-to-user") == "false":
    raise AssertionError("Add details action is not visible while the keyboard is open")
save_bounds = bounds(save)
edit_bounds = bounds(edit)

if save.attrib.get("visible-to-user") == "false":
    raise AssertionError("Add task action is not visible while the keyboard is open")
if save_bounds[3] > 900:
    raise AssertionError(f"Add task action is too low with keyboard open: {save_bounds}")
if edit_bounds[3] > save_bounds[1]:
    raise AssertionError(f"Task input overlaps action row: input={edit_bounds}, action={save_bounds}")

details_bounds = bounds(add_details)
with open(f"{SCREENSHOT_DIR}/add-details-center.txt", "w") as f:
    f.write(f"{(details_bounds[0] + details_bounds[2]) // 2} "
            f"{(details_bounds[1] + details_bounds[3]) // 2}\n")
PY

read details_x details_y < "$SCREENSHOT_DIR/add-details-center.txt"
adb shell input tap "$details_x" "$details_y"
sleep 1
adb shell uiautomator dump /sdcard/window-details-open.xml
adb pull /sdcard/window-details-open.xml "$SCREENSHOT_DIR/window-details-open.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-details-open.xml").getroot()
edits = [node for node in root.iter("node")
         if node.attrib.get("class") == "android.widget.EditText"]
if len(edits) != 2:
    raise AssertionError(f"Expanded details panel should expose two text fields, found {len(edits)}")
notes = edits[1]
x, y = center(notes.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/notes-input-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read notes_x notes_y < "$SCREENSHOT_DIR/notes-input-center.txt"
adb shell input tap "$notes_x" "$notes_y"
adb shell input text Reference_line_one
adb shell input keyevent KEYCODE_ENTER
adb shell input text Reference_line_two
sleep 1
adb exec-out screencap -p > "$SCREENSHOT_DIR/02c-details-keyboard-open.png"

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
         if node.attrib.get("text", "").startswith("CI_task")]
task = max(tasks, key=lambda node: center(node.attrib["bounds"])[1])
x, y = center(task.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/task-row-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
with open(f"{SCREENSHOT_DIR}/task-row-collapsed-height.txt", "w") as f:
    _, y1, _, y2 = map(int, re.findall(r"\d+", task.attrib["bounds"]))
    f.write(f"{y2 - y1}\n")
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
expanded = next((node for node in root.iter("node")
                 if node.attrib.get("content-desc", "").startswith(
                         "Expanded content for CI_task")), None)
if expanded is None:
    raise AssertionError("Tapping a task did not reveal its full content")
with open(f"{SCREENSHOT_DIR}/task-row-collapsed-height.txt") as f:
    collapsed_height = int(f.read().strip())
expanded_bounds = list(map(int, re.findall(r"\d+", expanded.attrib["bounds"])))
if expanded_bounds[3] - expanded_bounds[1] <= collapsed_height:
    raise AssertionError("Expanded task content did not wrap beyond one line")
texts = {node.attrib.get("text", "") for node in root.iter("node")}
if "Save" in texts:
    raise AssertionError("Tapping a task opened the edit sheet instead of its content")
if any(text.startswith("Impact:") or text in {"Quick win", "Long-press to edit"}
       for text in texts):
    raise AssertionError("Task expansion exposed priority metadata instead of task content")
if not any("Reference_line_one" in text and "Reference_line_two" in text for text in texts):
    raise AssertionError("Expanded task content did not show saved details")
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
edits = [node for node in root.iter("node")
         if node.attrib.get("class") == "android.widget.EditText"]
notes = edits[1] if len(edits) > 1 else None
if notes is None or "Reference_line_one" not in notes.attrib.get("text", ""):
    raise AssertionError("Editing task did not restore saved details")
list_pill = next((node for node in root.iter("node")
                  if node.attrib.get("content-desc", "").startswith("List: Work")), None)
if list_pill is None:
    raise AssertionError("Edit sheet did not expose current list picker")
x, y = center(list_pill.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/list-pill-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read list_pill_x list_pill_y < "$SCREENSHOT_DIR/list-pill-center.txt"
adb shell input tap "$list_pill_x" "$list_pill_y"
sleep 1
adb shell uiautomator dump /sdcard/window-list-picker.xml
adb pull /sdcard/window-list-picker.xml "$SCREENSHOT_DIR/window-list-picker.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-list-picker.xml").getroot()
no_list = next(node for node in root.iter("node") if node.attrib.get("text") == "No list")
x, y = center(no_list.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/no-list-choice-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read no_list_x no_list_y < "$SCREENSHOT_DIR/no-list-choice-center.txt"
adb shell input tap "$no_list_x" "$no_list_y"
sleep 1
adb shell uiautomator dump /sdcard/window-after-list-choice.xml
adb pull /sdcard/window-after-list-choice.xml "$SCREENSHOT_DIR/window-after-list-choice.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-after-list-choice.xml").getroot()
if not any(node.attrib.get("content-desc", "").startswith("List: No list")
           for node in root.iter("node")):
    raise AssertionError("List picker did not update task destination")
save = next(node for node in root.iter("node") if node.attrib.get("text") == "Save")
x, y = center(save.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/edit-save-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read edit_save_x edit_save_y < "$SCREENSHOT_DIR/edit-save-center.txt"
adb shell input tap "$edit_save_x" "$edit_save_y"
sleep 2
adb shell uiautomator dump /sdcard/window-after-task-move.xml
adb pull /sdcard/window-after-task-move.xml "$SCREENSHOT_DIR/window-after-task-move.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-after-task-move.xml").getroot()
if any(node.attrib.get("text", "").startswith("CI_task") for node in root.iter("node")):
    raise AssertionError("Task remained in its old list after moving to No list")
all_tab = next(node for node in root.iter("node") if node.attrib.get("text") == "All")
x, y = center(all_tab.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/all-tab-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read all_x all_y < "$SCREENSHOT_DIR/all-tab-center.txt"
adb shell input tap "$all_x" "$all_y"
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
