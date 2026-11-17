#!/usr/bin/env sh
set -eu

SCREENSHOT_DIR="app/build/modern-verification-screenshots"
mkdir -p "$SCREENSHOT_DIR"
trap 'adb logcat -d -s AndroidRuntime > "$SCREENSHOT_DIR/android-runtime.log" 2>&1 || true' EXIT

snapshot_ui() {
  adb shell uiautomator dump "/sdcard/$1.xml" >/dev/null
  adb pull "/sdcard/$1.xml" "$SCREENSHOT_DIR/$1.xml" >/dev/null
  adb exec-out screencap -p > "$SCREENSHOT_DIR/$1.png"
}

tap_label() {
  snapshot_ui "$1"
  python3 - "$SCREENSHOT_DIR/$1.xml" "$2" "$SCREENSHOT_DIR/target.txt" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, label, destination = sys.argv[1:]
root = ET.parse(path).getroot()
node = next((n for n in root.iter("node") if label.casefold() in
             (n.attrib.get("text", "").casefold(), n.attrib.get("content-desc", "").casefold())), None)
if node is None:
    labels = {value for n in root.iter("node") for value in
              (n.attrib.get("text", ""), n.attrib.get("content-desc", "")) if value}
    raise AssertionError(f"Missing {label!r}; visible labels: {sorted(labels)!r}")
x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
assert x2 > x1 and y2 > y1, f"Control {label!r} is clipped: {node.attrib['bounds']}"
with open(destination, "w") as output:
    output.write(f"{(x1+x2)//2} {(y1+y2)//2}\n")
PY
  read target_x target_y < "$SCREENSHOT_DIR/target.txt"
  adb shell input tap "$target_x" "$target_y"
}

assert_main() {
  snapshot_ui "$1"
  python3 - "$SCREENSHOT_DIR/$1.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
labels = {value for n in root.iter("node") for value in
          (n.attrib.get("text", ""), n.attrib.get("content-desc", "")) if value}
assert "Add task" in labels and any(value.endswith("All Tasks") for value in labels), f"Main screen is absent: {sorted(labels)!r}"
assert "New task" not in labels, "System back left the capture sheet open"
PY
}

assert_theme() {
  snapshot_ui "$1"
  adb shell run-as com.tj90.prioritytodo cat shared_prefs/priority_todo_ui.xml > "$SCREENSHOT_DIR/$1-prefs.xml"
  python3 - "$SCREENSHOT_DIR/$1.xml" "$SCREENSHOT_DIR/$1-prefs.xml" "$2" "$3" <<'PY'
import sys
import xml.etree.ElementTree as ET

ui_path, prefs_path, label, expected = sys.argv[1:]
ui = ET.parse(ui_path).getroot()
assert any(n.attrib.get("content-desc") == label for n in ui.iter("node")), f"Theme control did not show {label}"
prefs = ET.parse(prefs_path).getroot()
actual = next((n.text for n in prefs if n.attrib.get("name") == "theme"), None)
assert actual == expected, f"Theme choice was not persisted: expected {expected!r}, got {actual!r}"
PY
}

adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.tj90.prioritytodo android.permission.POST_NOTIFICATIONS
adb shell am start -W -n com.tj90.prioritytodo/.MainActivity >/dev/null
sleep 3
tap_label 01-onboarding "GOT IT"
sleep 1
assert_main 02-main

# Android 16 enforces edge-to-edge for this target. Compare real system-bar
# source frames with interactive app controls rather than assuming dp insets.
adb shell dumpsys window windows > "$SCREENSHOT_DIR/window-insets.txt"
python3 - "$SCREENSHOT_DIR/02-main.xml" "$SCREENSHOT_DIR/window-insets.txt" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

ui_path, insets_path = sys.argv[1:]
dump = open(insets_path).read()
pattern = r"(?:mType|type)=(statusBars|navigationBars)[^{}]*?(?:mFrame|frame)=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"
frames = {kind: [] for kind in ("statusBars", "navigationBars")}
for kind, x1, y1, x2, y2 in re.findall(pattern, dump):
    bounds = tuple(map(int, (x1, y1, x2, y2)))
    if bounds[2] > bounds[0] and bounds[3] > bounds[1]:
        frames[kind].append(bounds)
# Recent window dumps expose bar providers on their own windows rather than
# listing InsetsSource objects. Use those real window frames when needed.
for window in re.split(r"(?m)^  Window #\d+ ", dump)[1:]:
    kind = re.search(r"\bty=(STATUS_BAR|NAVIGATION_BAR)(?:\s|$)", window)
    frame = re.search(r"(?m)^\s+Frames:.*?\bframe=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", window)
    if kind is not None and frame is not None and "isOnScreen=true" in window:
        name = "statusBars" if kind[1] == "STATUS_BAR" else "navigationBars"
        bounds = tuple(map(int, frame.groups()))
        if bounds[2] > bounds[0] and bounds[3] > bounds[1]:
            frames[name].append(bounds)
assert all(frames.values()), f"Could not read real system-bar insets; inspect {insets_path}"
status_bottom = max(frame[3] for frame in frames["statusBars"])
nav_top = min(frame[1] for frame in frames["navigationBars"])
assert nav_top > status_bottom, f"Invalid safe area: status={frames['statusBars']!r}, navigation={frames['navigationBars']!r}"
root = ET.parse(ui_path).getroot()
controls = [n for n in root.iter("node") if n.attrib.get("text", "").endswith("All Tasks")
            or n.attrib.get("content-desc") in {"Search tasks", "Add task", "Previous list", "Next list"}]
assert len(controls) >= 5, "Expected app bar, search, add action and both list-dock arrows"
for node in controls:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    label = node.attrib.get("content-desc") or node.attrib.get("text")
    assert status_bottom <= y1 < y2 <= nav_top, f"Control {label!r} overlaps system bars: {node.attrib['bounds']}, safe vertical range=[{status_bottom},{nav_top}]"
print(f"Android 16 safe content area verified between y={status_bottom} and y={nav_top}")
PY

# KEYCODE_BACK is dispatched through the platform back dispatcher on API 36.
# The first event hides IME; the second must dismiss the sheet, retaining home.
tap_label 03-open-add "Add task"
sleep 2
adb shell dumpsys input_method > "$SCREENSHOT_DIR/keyboard-before-back.txt"
if ! grep -q 'mInputShown=true' "$SCREENSHOT_DIR/keyboard-before-back.txt"; then
  echo "Capture did not open its keyboard on Android 16" >&2
  exit 1
fi
adb shell input keyevent KEYCODE_BACK
sleep 1
snapshot_ui 04-add-keyboard-hidden
python3 - "$SCREENSHOT_DIR/04-add-keyboard-hidden.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
assert any(n.attrib.get("text") == "New task" for n in root.iter("node")), "IME dismissal unexpectedly dismissed the capture sheet"
PY
adb shell input keyevent KEYCODE_BACK
sleep 1
assert_main 05-after-system-back

tap_label 06-reopen-add "Add task"
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 1
tap_label 07-cancel-add "Cancel"
sleep 1
assert_main 08-after-cancel

# Test all theme transitions and prove explicit night mode survives process death.
tap_label 09-day-to-night "Day theme"
sleep 1
assert_theme 10-night "Night theme" night
adb shell am force-stop com.tj90.prioritytodo
adb shell am start -W -n com.tj90.prioritytodo/.MainActivity >/dev/null
sleep 2
assert_theme 11-night-after-restart "Night theme" night
tap_label 12-night-to-auto "Night theme"
sleep 1
assert_theme 13-auto "Auto theme" system
tap_label 14-auto-to-day "Auto theme"
sleep 1
assert_theme 15-day "Day theme" day
assert_main 16-final-home
