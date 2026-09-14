import argparse
import ast
import csv
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description='Verify actual environment preview language and gameplay evidence.')
parser.add_argument('directory', nargs='?', default=str(root / 'build/environment-preview'))
out = Path(parser.parse_args().directory)
tree = ast.parse((root / 'scripts/verify-languages.py').read_text(encoding='utf-8'))
allowed = {}
for node in tree.body:
    if isinstance(node, ast.Assign) and isinstance(node.targets[0], ast.Name) and node.targets[0].id in {'keycaps', 'code_lines'}:
        allowed[node.targets[0].id] = ast.literal_eval(node.value)
names = {'water-walk', 'water-dash', 'water-landing', 'capacitor-before', 'capacitor-discharge',
         'supplies-before', 'supplies-after', 'heap-low-steps', 'heap-upper-route', 'boss-arrival'}
expected = {f'{language}-{name}-{width}' for language in ('en', 'zh-CN') for name in names for width in (960, 600)}
seen = set()
errors = []
scene = ''
for line in (out / 'displayed-text.txt').read_text(encoding='utf-8').splitlines():
    if line.startswith('=== ') and line.endswith(' ==='):
        scene = line[4:-4]; seen.add(scene); continue
    if scene.startswith('en-') and re.search(r'[\u3400-\u9fff]', line): errors.append((scene, line))
    if scene.startswith('zh-CN-') and line not in allowed['code_lines'] and not re.fullmatch(r'0x[0-9A-F]+', line):
        if any(word not in allowed['keycaps'] for word in re.findall(r'[A-Za-z]{3,}', line)):
            errors.append((scene, line))
assert seen == expected, f'Missing/unexpected captures: {expected ^ seen}'
assert not errors, f'Mixed language: {errors}'
rows = {row['capture']: row for row in csv.DictReader((out / 'state-evidence.csv').open(encoding='utf-8'))}
assert set(rows) == expected
for language in ('en', 'zh-CN'):
    for width in (960, 600):
        def state(name): return rows[f'{language}-{name}-{width}']
        for name in ('water-walk', 'water-dash', 'water-landing'):
            row = state(name)
            assert float(row['feet']) == 480 and row['grounded'] == 'true' and int(row['ripples']) > 0 and int(row['droplets']) > 0, row
        assert state('water-walk')['right_held'] == 'true'
        assert state('water-dash')['dashing'] == 'true'
        assert float(state('heap-low-steps')['feet']) == 432
        assert float(state('heap-upper-route')['feet']) == 400
        assert int(state('capacitor-discharge')['props']) == int(state('capacitor-before')['props']) - 1
        assert int(state('supplies-after')['bombs']) == int(state('supplies-before')['bombs']) + 1
        assert int(state('supplies-after')['hp']) == int(state('supplies-before')['hp']) + 15
        warning = state('boss-arrival')
        assert warning['state'] == 'BOSS_WARNING' and 0 < int(warning['boss_arrival_ticks']) < 125
        assert warning['hp'] == '100' and warning['bombs'] == '3'
for name in expected: assert (out / (name + '.png')).is_file(), name
print('40 actual panel captures passed: language parity, real contact/dash/landing, platform heights, prop consumption, supply rewards and Boss preparation.')

