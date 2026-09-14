"""Select one explicitly marked Android test interval without deleting raw logs.
A missing marker is a test failure, not an empty log interpreted as success.
"""
def after_marker(text, token):
    lines = text.splitlines(keepends=True)
    positions = [i for i, line in enumerate(lines) if 'LukeCase' in line and line.rstrip().endswith(token)]
    if len(positions) != 1:
        raise AssertionError('Expected exactly one test boundary marker: ' + token)
    return ''.join(lines[positions[0]+1:])
