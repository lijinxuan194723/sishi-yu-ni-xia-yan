"""Read-only test selection. Never change the app or relax a failed assertion."""
import re
import time
import xml.etree.ElementTree as ET


def choose_control(tree, label):
    """Only a unique enabled interactive node whose center is on this display."""
    width, height = int(tree.get('width', '0')), int(tree.get('height', '0'))
    choices = {}
    for node in tree.iter('node'):
        if label not in (node.get('text'), node.get('content-desc')):
            continue
        if node.get('clickable') != 'true' or node.get('enabled') != 'true':
            continue
        box = tuple(map(int, re.findall(r'-?\d+', node.get('bounds', ''))))
        if len(box) != 4:
            continue
        left, top, right, bottom = box
        if right > left and bottom > top and 0 <= (left + right) / 2 < width and 0 <= (top + bottom) / 2 < height:
            choices[box] = node
    if len(choices) > 1:
        raise AssertionError('Ambiguous interactive control: ' + label)
    return next(iter(choices.values()), None)


def wait_rotation(dump, expected, timeout=15):
    """Wait for measured rotation and display size, not a fixed one-second sleep."""
    end = time.monotonic() + timeout
    previous, stable = None, 0
    while time.monotonic() < end:
        root = ET.fromstring(dump())
        rotation = int(root.get('rotation', '-1'))
        width, height = int(root.get('width', '0')), int(root.get('height', '0'))
        signature = (rotation, width, height)
        correct = rotation == expected and min(width, height) > 0 and ((width < height) == (expected % 2 == 0))
        stable = stable + 1 if correct and signature == previous else 0
        if stable >= 3:
            return
        previous = signature
        time.sleep(.25)
    raise AssertionError('Display rotation did not stabilize: ' + str(previous))


def bound_renderer(services, processes, host, package):
    """Select by explicit ServiceRecord -> Client AppBindRecord relationship.

    Refuse stale host IDs, shared renderers, missing bindings and ambiguity.
    A separate WebView renderer owned by another app is never a candidate.
    """
    if not re.fullmatch(r'[1-9][0-9]*', host):
        return None
    running = dict(re.findall(r'^\s*(\d+)\s+(\S+)\s*$', processes, re.M))
    if running.get(host) != package:
        return None
    targets = set()
    for block in re.split(r'(?m)^\s*\* ServiceRecord\{', services)[1:]:
        match = re.search(r'^\s*app=ProcessRecord\{\S+\s+(\d+):([^/\s]+)/[^}\n]*\}', block, re.M)
        if not match:
            continue
        pid, name = match.groups()
        if pid == host or 'sandboxed_process' not in name or running.get(pid) != name:
            continue
        clients = set(re.findall(r'Client AppBindRecord\{[^\n]*?ProcessRecord\{\S+\s+(\d+):([^/\s]+)/', block))
        if clients == {(host, package)}:
            targets.add(pid)
    return next(iter(targets)) if len(targets) == 1 else None
