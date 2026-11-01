"""Print the centre "x y" of a UI node from a uiautomator dump.

usage: ui_find.py dump.xml TARGET
TARGET is "id:<name>" for a view id of the app (e.g. id:buttonContinue),
"~text" for a substring of the text/content-desc, or the exact text.
"""
import re
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]

try:
    root = ET.parse(path).getroot()
except (ET.ParseError, FileNotFoundError):
    sys.exit(0)


def matches(node):
    if target.startswith("id:"):
        return node.get("resource-id", "").endswith(":id/" + target[3:])
    values = (node.get("text", ""), node.get("content-desc", ""))
    if target.startswith("~"):
        return any(target[1:] in value for value in values)
    return target in values


for node in root.iter("node"):
    if matches(node):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        if x2 > x1 and y2 > y1:
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
