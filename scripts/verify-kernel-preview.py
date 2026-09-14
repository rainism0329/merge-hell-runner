"""Audit bilingual fourth-world captures against their actual rail, switch and boss simulation state."""
import argparse
import ast
import csv
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', nargs='?', default='build/kernel-preview')
out = Path(parser.parse_args().directory)
root = Path(__file__).resolve().parents[1]
allowed = {}
for node in ast.parse((root / 'scripts/verify-languages.py').read_text(encoding='utf8')).body:
    if isinstance(node, ast.Assign) and isinstance(node.targets[0], ast.Name) and node.targets[0].id in {'keycaps', 'code_lines'}:
        allowed[node.targets[0].id] = ast.literal_eval(node.value)
names = {'route', 'warning', 'active', 'poweroff', 'pause', 'arrival', 'armed', 'chargewarning', 'exposed'}
expected = {f'{language}-kernel-{name}-{width}' for language in ('en', 'zh-CN') for name in names for width in (600, 960)}
scenes = set()
scene = ''
for line in (out / 'displayed-text.txt').read_text(encoding='utf8').splitlines():
    if line.startswith('=== ') and line.endswith(' ==='):
        scene = line[4:-4]
        assert scene not in scenes, f'Duplicate capture: {scene}'
        scenes.add(scene)
        continue
    assert 'kernel.' not in line, (scene, 'Unresolved localization key', line)
    if scene.startswith('en-'):
        assert not re.search(r'[\u3400-\u9fff]', line), (scene, line)
    elif scene.startswith('zh-CN-') and line not in allowed['code_lines'] and not re.fullmatch(r'0x[0-9A-F]+', line):
        assert all(word in allowed['keycaps'] for word in re.findall(r'[A-Za-z]{3,}', line)), (scene, line)
assert scenes == expected, expected ^ scenes
with (out / 'state-evidence.csv').open(encoding='utf8') as handle:
    rows = list(csv.DictReader(handle))
states = {row['scene']: row for row in rows}
assert len(rows) == len(expected) and set(states) == expected
for language in ('en', 'zh-CN'):
    for width in (600, 960):
        def get(name):
            return states[f'{language}-kernel-{name}-{width}']
        assert get('route')['station_state'] == 'READY'
        assert get('warning')['rail_phase'] == 'WARNING' and 1 <= int(get('warning')['rail_ticks']) <= 75
        assert get('active')['rail_phase'] == 'ACTIVE' and int(get('active')['active_rails']) > 0
        assert get('poweroff')['station_state'] == 'DISABLED' and get('poweroff')['rail_phase'] == 'SAFE'
        assert get('poweroff')['active_rails'] == '0'
        assert int(get('poweroff')['score']) == int(get('route')['score']) + 250
        assert get('poweroff')['platforms'] == get('route')['platforms'] == '3'
        assert get('pause')['state'] == 'PAUSED' and get('pause')['station_state'] == 'DISABLED'
        assert get('arrival')['state'] == 'BOSS_WARNING'
        assert get('armed')['state'] == 'BOSS_FIGHT' and get('armed')['station_state'] == 'ARMED'
        assert get('armed')['station_id'] == '102' and get('armed')['platforms'] == '2'
        assert get('chargewarning')['station_state'] == 'ARMED' and 1 <= int(get('chargewarning')['boss_warning']) <= 60
        assert get('chargewarning')['boss_dash'] == 'false' and get('chargewarning')['boss_expose'] == '0'
        assert get('exposed')['station_state'] == 'COOLDOWN' and get('exposed')['boss_dash'] == 'false'
        assert 1 <= int(get('exposed')['boss_expose']) <= 150
        assert get('exposed')['boss_expose'] == get('exposed')['controller_expose']
        assert get('exposed')['active_rails'] == '0' and get('exposed')['score'] == '0'
        for name in names:
            row = get(name)
            assert row['hp'] == '100', (row['scene'], 'Safe fixture took damage')
            assert 0 <= int(row['active_rails']) <= int(row['rail_count']) <= 2
for name in expected:
    assert (out / (name + '.png')).is_file(), name
print('36 Kernel captures passed: bilingual UI, real rail warning/current, safe switch zone, one-time reward, fixed platforms, pause, natural charge and fault exposure.')
