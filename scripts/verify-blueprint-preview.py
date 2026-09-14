"""Audit the third-world captures produced by BlueprintPreview, including real gameplay state."""
import argparse
import ast
import csv
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', nargs='?', default='build/blueprint-preview')
out = Path(parser.parse_args().directory)
root = Path(__file__).resolve().parents[1]
allowed = {}
for node in ast.parse((root / 'scripts/verify-languages.py').read_text(encoding='utf8')).body:
    if isinstance(node, ast.Assign) and isinstance(node.targets[0], ast.Name) and node.targets[0].id in {'keycaps', 'code_lines'}:
        allowed[node.targets[0].id] = ast.literal_eval(node.value)
names = {'route', 'warning', 'overload', 'collapse', 'cleared', 'pause', 'arrival', 'boss', 'exposed'}
expected = {f'{language}-blueprint-{name}-{width}' for language in ('en', 'zh-CN') for name in names for width in (600, 960)}
scenes = set()
scene = ''
for line in (out / 'displayed-text.txt').read_text(encoding='utf8').splitlines():
    if line.startswith('=== ') and line.endswith(' ==='):
        scene = line[4:-4]; scenes.add(scene); continue
    if scene.startswith('en-'):
        assert not re.search(r'[\u3400-\u9fff]', line), (scene, line)
    elif scene.startswith('zh-CN-') and line not in allowed['code_lines'] and not re.fullmatch(r'0x[0-9A-F]+', line):
        assert all(word in allowed['keycaps'] for word in re.findall(r'[A-Za-z]{3,}', line)), (scene, line)
assert scenes == expected, expected ^ scenes
with (out / 'state-evidence.csv').open(encoding='utf8') as f:
    states = {row['scene']: row for row in csv.DictReader(f)}
assert set(states) == expected
for language in ('en', 'zh-CN'):
    for width in (600, 960):
        def get(name): return states[f'{language}-blueprint-{name}-{width}']
        assert get('route')['support_state'] == 'ONLINE'
        assert int(get('warning')['scans']) > 0
        assert get('overload')['support_state'] == 'OVERLOADING'
        assert get('collapse')['support_state'] == 'COLLAPSING' and get('collapse')['scans'] == '0'
        assert get('cleared')['support_state'] == 'DISABLED'
        assert int(get('cleared')['platforms']) == int(get('route')['platforms']) - 1
        assert int(get('cleared')['score']) == int(get('route')['score']) + 350
        assert get('pause')['state'] == 'PAUSED'
        assert get('arrival')['state'] == 'BOSS_WARNING'
        assert get('boss')['state'] == 'BOSS_FIGHT'
        assert get('exposed')['support_hp'] == '0' and int(get('exposed')['boss_expose']) > 0
        assert get('exposed')['hp'] == '100'
for name in expected: assert (out / (name + '.png')).is_file(), name
print('36 Blueprint captures passed: bilingual UI, real overload/collapse, scan clearance, one-time reward, pause and boss exposure.')
