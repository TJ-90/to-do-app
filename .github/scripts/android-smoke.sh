#!/usr/bin/env sh
set -eu

SCREENSHOT_DIR="app/build/verification-screenshots"
mkdir -p "$SCREENSHOT_DIR"

WEB_SERVER_PID=""
cleanup_web_server() {
  if [ -n "$WEB_SERVER_PID" ]; then
    kill "$WEB_SERVER_PID" 2>/dev/null || true
    wait "$WEB_SERVER_PID" 2>/dev/null || true
    WEB_SERVER_PID=""
  fi
}
trap cleanup_web_server EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

rm -f web/.data/sync-state.json
node web/server.js > "$SCREENSHOT_DIR/web-server.log" 2>&1 &
WEB_SERVER_PID=$!

server_ready=0
for attempt in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8787/api/health > "$SCREENSHOT_DIR/web-health.json"; then
    server_ready=1
    break
  fi
  if ! kill -0 "$WEB_SERVER_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done
if [ "$server_ready" != 1 ]; then
  echo "Local web server did not become healthy" >&2
  tail -n 100 "$SCREENSHOT_DIR/web-server.log" >&2 || true
  exit 1
fi

wait_for_server_task() {
  expected_title="$1"
  output_file="$2"
  python3 - "$expected_title" "$output_file" <<'PY'
import json
import sys
import time
import urllib.error
import urllib.request

expected_title, output_file = sys.argv[1:]
last_error = None
for _ in range(30):
    try:
        with urllib.request.urlopen("http://127.0.0.1:8787/api/state", timeout=2) as response:
            body = response.read().decode("utf-8")
        state = json.loads(body)
        with open(output_file, "w", encoding="utf-8") as destination:
            json.dump(state, destination, indent=2)
            destination.write("\n")
        if any(task.get("title") == expected_title for task in state.get("tasks", [])):
            raise SystemExit(0)
        last_error = f"task {expected_title!r} was absent"
    except (OSError, ValueError, urllib.error.URLError) as error:
        last_error = str(error)
    time.sleep(1)
raise AssertionError(f"Timed out waiting for server task {expected_title!r}: {last_error}")
PY
}

tap_ui_label() {
  remote_dump="$1"
  local_dump="$2"
  label="$3"
  adb shell uiautomator dump "$remote_dump" >/dev/null
  adb pull "$remote_dump" "$local_dump" >/dev/null
  python3 - "$local_dump" "$label" "$SCREENSHOT_DIR/ui-target-center.txt" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

dump_file, label, output_file = sys.argv[1:]
root = ET.parse(dump_file).getroot()
node = next((item for item in root.iter("node")
             if label in {item.attrib.get("text", ""), item.attrib.get("content-desc", "")}), None)
if node is None:
    visible = sorted({value for item in root.iter("node")
                      for value in (item.attrib.get("text", ""), item.attrib.get("content-desc", ""))
                      if value})
    raise AssertionError(f"Could not find UI label {label!r}; visible labels: {visible!r}")
x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
with open(output_file, "w", encoding="utf-8") as destination:
    destination.write(f"{(x1 + x2) // 2} {(y1 + y2) // 2}\n")
PY
  read target_x target_y < "$SCREENSHOT_DIR/ui-target-center.txt"
  adb shell input tap "$target_x" "$target_y"
}

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

cat > /tmp/priority-todo-legacy-prefs.xml <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="tasks">[{&quot;id&quot;:&quot;ci-legacy-task&quot;,&quot;title&quot;:&quot;Legacy_low_priority_task&quot;,&quot;notes&quot;:&quot;Migration fixture&quot;,&quot;impact&quot;:&quot;L&quot;,&quot;effort&quot;:&quot;H&quot;,&quot;dependency&quot;:&quot;None&quot;,&quot;category&quot;:&quot;Legacy_list&quot;,&quot;urgent&quot;:false,&quot;quickTask&quot;:false,&quot;snoozed&quot;:false,&quot;recurringMit&quot;:false,&quot;completed&quot;:false,&quot;createdAt&quot;:1700000000000,&quot;updatedAt&quot;:1700000000000,&quot;reminderAt&quot;:0,&quot;reminderRepeatUnit&quot;:&quot;none&quot;,&quot;reminderRepeatEvery&quot;:1}]</string>
    <string name="categories">[&quot;Legacy_list&quot;]</string>
</map>
EOF
adb shell run-as com.tj90.prioritytodo mkdir -p shared_prefs
adb shell "run-as com.tj90.prioritytodo sh -c 'cat > shared_prefs/priority_todo_store.xml'" \
  < /tmp/priority-todo-legacy-prefs.xml
adb shell run-as com.tj90.prioritytodo chmod 600 shared_prefs/priority_todo_store.xml

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

python3 - <<'PY'
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"
root = ET.parse(f"{SCREENSHOT_DIR}/window-launch.xml").getroot()
labels = {value for node in root.iter("node")
          for value in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
          if value}
for expected in ("Legacy_low_priority_task", "Legacy_list"):
    if expected not in labels:
        raise AssertionError(f"Legacy migration did not render {expected!r}; found {sorted(labels)!r}")
PY

migration_written=0
for attempt in $(seq 1 10); do
  if adb shell run-as com.tj90.prioritytodo cat shared_prefs/priority_todo_store.xml \
      | grep -q 'category_states_v1'; then
    migration_written=1
    break
  fi
  sleep 1
done
if [ "$migration_written" != 1 ]; then
  echo "Legacy category migration was not persisted before restart" >&2
  exit 1
fi

adb shell am force-stop com.tj90.prioritytodo
adb shell am start -W -n com.tj90.prioritytodo/.MainActivity >/dev/null
sleep 2
adb shell uiautomator dump /sdcard/window-migration-restart.xml >/dev/null
adb pull /sdcard/window-migration-restart.xml "$SCREENSHOT_DIR/window-migration-restart.xml" >/dev/null
adb shell run-as com.tj90.prioritytodo cat shared_prefs/priority_todo_store.xml \
  > "$SCREENSHOT_DIR/migrated-shared-prefs.xml"

python3 - <<'PY'
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"
root = ET.parse(f"{SCREENSHOT_DIR}/window-migration-restart.xml").getroot()
labels = {value for node in root.iter("node")
          for value in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
          if value}
for expected in ("Legacy_low_priority_task", "Legacy_list"):
    if expected not in labels:
        raise AssertionError(f"Migrated value disappeared after restart: {expected!r}")

prefs = ET.parse(f"{SCREENSHOT_DIR}/migrated-shared-prefs.xml").getroot()
if not any(node.attrib.get("name") == "category_states_v1" for node in prefs):
    raise AssertionError("Legacy list migration did not persist category_states_v1")
PY

# Verify the overflow exposes sync setup and manual sync, then configure the emulator endpoint.
python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-launch.xml").getroot()
more = next(node for node in root.iter("node")
            if node.attrib.get("content-desc") == "More options")
x, y = center(more.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/more-options-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read more_x more_y < "$SCREENSHOT_DIR/more-options-center.txt"
adb shell input tap "$more_x" "$more_y"
sleep 1
adb shell uiautomator dump /sdcard/window-overflow.xml
adb pull /sdcard/window-overflow.xml "$SCREENSHOT_DIR/window-overflow.xml"

python3 - <<'PY'
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"
root = ET.parse(f"{SCREENSHOT_DIR}/window-overflow.xml").getroot()
labels = {
    value
    for node in root.iter("node")
    for value in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
    if value
}
for expected in ("Sync with web", "Sync now"):
    if expected not in labels:
        raise AssertionError(f"Overflow missing {expected!r}; found {sorted(labels)!r}")
PY

tap_ui_label /sdcard/window-overflow.xml "$SCREENSHOT_DIR/window-overflow.xml" "Sync with web"
sleep 1
adb shell uiautomator dump /sdcard/window-sync-setup.xml >/dev/null
adb pull /sdcard/window-sync-setup.xml "$SCREENSHOT_DIR/window-sync-setup.xml" >/dev/null

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"
root = ET.parse(f"{SCREENSHOT_DIR}/window-sync-setup.xml").getroot()
edits = [node for node in root.iter("node")
         if node.attrib.get("class") == "android.widget.EditText"]
if len(edits) != 3:
    raise AssertionError(f"Sync setup should expose URL, Client ID, and Client Secret; found {len(edits)} fields")
endpoint = edits[0]
if endpoint is None or endpoint.attrib.get("text") != "http://10.0.2.2:8787":
    raise AssertionError(f"Sync endpoint was not prefilled correctly: {None if endpoint is None else endpoint.attrib.get('text')!r}")
save = next((node for node in root.iter("node")
             if node.attrib.get("text", "").upper() == "SAVE"), None)
if save is None:
    raise AssertionError("Sync setup dialog did not expose Save")
x1, y1, x2, y2 = map(int, re.findall(r"\d+", save.attrib["bounds"]))
with open(f"{SCREENSHOT_DIR}/sync-save-center.txt", "w", encoding="utf-8") as destination:
    destination.write(f"{(x1 + x2) // 2} {(y1 + y2) // 2}\n")
PY

read sync_save_x sync_save_y < "$SCREENSHOT_DIR/sync-save-center.txt"
adb shell input tap "$sync_save_x" "$sync_save_y"
wait_for_server_task "Legacy_low_priority_task" "$SCREENSHOT_DIR/server-state-after-legacy-sync.json"

python3 - <<'PY'
import json

with open("app/build/verification-screenshots/server-state-after-legacy-sync.json", encoding="utf-8") as source:
    state = json.load(source)
legacy = next((task for task in state["tasks"] if task.get("id") == "ci-legacy-task"), None)
if legacy is None or legacy.get("title") != "Legacy_low_priority_task" or legacy.get("category") != "Legacy_list":
    raise AssertionError(f"GET /api/state did not contain the migrated legacy task: {legacy!r}")
category = next((value for value in state["categories"] if value.get("name") == "Legacy_list"), None)
if category is None or category.get("updatedAt", 0) <= category.get("deletedAt", 0):
    raise AssertionError(f"GET /api/state did not contain the active migrated list: {category!r}")
PY

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

wait_for_server_task "CI_task_with_a_long_title_that_should_wrap_fully" \
  "$SCREENSHOT_DIR/server-state-after-android-add.json"

python3 - <<'PY'
import json

with open("app/build/verification-screenshots/server-state-after-android-add.json", encoding="utf-8") as source:
    state = json.load(source)
matches = [task for task in state["tasks"]
           if task.get("title") == "CI_task_with_a_long_title_that_should_wrap_fully"]
if len(matches) != 1:
    raise AssertionError(f"Expected one exact Android-created task, found {len(matches)}")
task = matches[0]
if task.get("notes") != "Reference_line_one\nReference_line_two" or task.get("category") != "Work":
    raise AssertionError(f"Android-created task reached /api/state with unexpected data: {task!r}")
PY

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
tasks = [node for node in root.iter("node")
         if node.attrib.get("text", "").startswith("CI_task")]
task = max(tasks, key=lambda node: center(node.attrib["bounds"])[1])
x, y = center(task.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/moved-task-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read moved_task_x moved_task_y < "$SCREENSHOT_DIR/moved-task-center.txt"
adb shell input swipe "$moved_task_x" "$moved_task_y" "$moved_task_x" "$moved_task_y" 700
sleep 1
adb shell uiautomator dump /sdcard/window-after-move-reopen.xml
adb pull /sdcard/window-after-move-reopen.xml "$SCREENSHOT_DIR/window-after-move-reopen.xml"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET

SCREENSHOT_DIR = "app/build/verification-screenshots"

def center(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2

root = ET.parse(f"{SCREENSHOT_DIR}/window-after-move-reopen.xml").getroot()
if not any(node.attrib.get("content-desc", "").startswith("List: No list")
           for node in root.iter("node")):
    raise AssertionError("Moved task did not persist its No list destination")
cancel = next(node for node in root.iter("node") if node.attrib.get("text") == "Cancel")
x, y = center(cancel.attrib["bounds"])
with open(f"{SCREENSHOT_DIR}/move-verify-cancel-center.txt", "w") as f:
    f.write(f"{x} {y}\n")
PY

read move_cancel_x move_cancel_y < "$SCREENSHOT_DIR/move-verify-cancel-center.txt"
adb shell input tap "$move_cancel_x" "$move_cancel_y"
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

cat > "$SCREENSHOT_DIR/web-sync-request.json" <<'EOF'
{
  "tasks": [
    {
      "id": "ci-web-synced-task",
      "title": "Web_synced_task",
      "notes": "Created by the localhost smoke test",
      "impact": "H",
      "effort": "H",
      "dependency": "None",
      "category": null,
      "urgent": false,
      "quickTask": false,
      "snoozed": false,
      "recurringMit": false,
      "completed": false,
      "createdAt": 2000000000000,
      "updatedAt": 2000000000000,
      "reminderAt": 0,
      "reminderRepeatUnit": "none",
      "reminderRepeatEvery": 1
    }
  ],
  "taskTombstones": [],
  "categories": []
}
EOF
curl -fsS -H 'Content-Type: application/json' \
  --data-binary @"$SCREENSHOT_DIR/web-sync-request.json" \
  http://127.0.0.1:8787/api/sync > "$SCREENSHOT_DIR/server-state-after-web-post.json"

python3 - <<'PY'
import json

directory = "app/build/verification-screenshots"
with open(f"{directory}/web-sync-request.json", encoding="utf-8") as source:
    expected = json.load(source)["tasks"][0]
with open(f"{directory}/server-state-after-web-post.json", encoding="utf-8") as source:
    state = json.load(source)
actual = next((task for task in state["tasks"] if task.get("id") == expected["id"]), None)
if actual != expected:
    raise AssertionError(f"Server did not preserve the exact web task schema/timestamps: {actual!r}")
PY

tap_ui_label /sdcard/window-before-manual-sync.xml \
  "$SCREENSHOT_DIR/window-before-manual-sync.xml" "More options"
sleep 1
tap_ui_label /sdcard/window-manual-sync-menu.xml \
  "$SCREENSHOT_DIR/window-manual-sync-menu.xml" "Sync now"

android_sync_visible=0
for attempt in $(seq 1 30); do
  sleep 1
  adb shell uiautomator dump /sdcard/window-after-web-sync.xml >/dev/null
  adb pull /sdcard/window-after-web-sync.xml "$SCREENSHOT_DIR/window-after-web-sync.xml" >/dev/null
  if python3 - <<'PY'
import xml.etree.ElementTree as ET

root = ET.parse("app/build/verification-screenshots/window-after-web-sync.xml").getroot()
labels = {value for node in root.iter("node")
          for value in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
          if value}
raise SystemExit(0 if "Web_synced_task" in labels else 1)
PY
  then
    android_sync_visible=1
    break
  fi
done
if [ "$android_sync_visible" != 1 ]; then
  echo "Web_synced_task did not appear in Android after manual sync" >&2
  tail -n 100 "$SCREENSHOT_DIR/web-server.log" >&2 || true
  exit 1
fi
adb exec-out screencap -p > "$SCREENSHOT_DIR/05-after-web-sync.png"

adb shell settings put system font_scale 1.0 || true
adb shell wm size reset || true
adb shell wm density reset || true
