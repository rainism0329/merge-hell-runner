"""Audit bilingual fifth-world captures against actual echo, link and boss simulation state."""
import argparse
import ast
import csv
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', nargs='?', default='build/singularity-preview')
out = Path(parser.parse_args().directory)
root = Path(__file__).resolve().parents[1]
allowed = {}
for node in ast.parse((root / 'scripts/verify-languages.py').read_text(encoding='utf8')).body:
    if isinstance(node, ast.Assign) and isinstance(node.targets[0], ast.Name) and node.targets[0].id in {'keycaps', 'code_lines'}:
        allowed[node.targets[0].id] = ast.literal_eval(node.value)
names = {'route', 'memorywarning', 'memoryactive', 'stabilized', 'blueprintactive', 'kernelactive',
         'arrival', 'armed', 'volleywarning', 'exposed', 'cooldown', 'phase2', 'victory'}
expected = {f'{language}-singularity-{name}-{width}' for language in ('en', 'zh-CN') for name in names for width in (600, 960)}
scenes = set()
scene_text = {}
scene = ''
for line in (out / 'displayed-text.txt').read_text(encoding='utf8').splitlines():
    if line.startswith('=== ') and line.endswith(' ==='):
        scene = line[4:-4]
        assert scene not in scenes, f'Duplicate capture: {scene}'
        scenes.add(scene)
        scene_text[scene] = []
        continue
    scene_text.setdefault(scene, []).append(line)
    assert not re.search(r'(?:singularity|kernel|blueprint|heap)\.', line), (scene, 'Unresolved localization key', line)
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
            return states[f'{language}-singularity-{name}-{width}']
        assert get('route')['anchor_state'] == 'READY'
        assert get('memorywarning')['echo'] == 'MEMORY' and get('memorywarning')['hazard_phase'] == 'WARNING'
        assert 1 <= int(get('memorywarning')['hazard_ticks']) <= 75
        for name, echo in (('memoryactive', 'MEMORY'), ('blueprintactive', 'BLUEPRINT'), ('kernelactive', 'KERNEL')):
            assert get(name)['echo'] == echo and get(name)['hazard_phase'] == 'ACTIVE'
            assert 1 <= int(get(name)['hazard_ticks']) <= 90
        assert get('stabilized')['anchor_state'] == 'STABILIZED' and get('stabilized')['hazard_count'] == '0'
        assert 1 <= int(get('stabilized')['anchor_ticks']) <= 600
        assert int(get('stabilized')['score']) == int(get('route')['score']) + 250
        assert get('stabilized')['platforms'] == get('route')['platforms'] == '3'
        assert get('arrival')['state'] == 'BOSS_WARNING'
        assert get('armed')['state'] == 'BOSS_FIGHT' and get('armed')['anchor_state'] == 'ARMED'
        assert get('armed')['anchor_id'] == '101' and get('armed')['armed_count'] == '1'
        assert 1 <= int(get('armed')['anchor_ticks']) <= 500
        assert get('armed')['platforms'] == '2' and get('armed')['hazard_count'] == '0'
        assert get('volleywarning')['anchor_state'] == 'ARMED'
        assert 1 <= int(get('volleywarning')['boss_warning']) <= 75
        assert int(get('volleywarning')['predicted_shots']) > 0 and get('volleywarning')['boss_expose'] == '0'
        assert get('exposed')['anchor_state'] == 'COOLDOWN' and get('exposed')['cooldown_count'] == '2'
        assert 1 <= int(get('exposed')['boss_expose']) <= 150
        assert get('exposed')['boss_expose'] == get('exposed')['controller_expose']
        assert get('exposed')['hazard_count'] == get('exposed')['predicted_shots'] == get('exposed')['score'] == '0'
        assert get('cooldown')['anchor_state'] == 'COOLDOWN' and get('cooldown')['cooldown_count'] == '2'
        assert get('cooldown')['boss_expose'] == get('cooldown')['controller_expose'] == '0'
        assert 1 <= int(get('cooldown')['anchor_ticks']) < 600
        assert get('cooldown')['hazard_count'] == get('cooldown')['predicted_shots'] == get('cooldown')['boss_warning'] == '0'
        locale_lines = (root / f'src/main/resources/game/i18n/messages_{language}.properties').read_text(encoding='utf8').splitlines()
        cooldown_text = next(line.split('=', 1)[1] for line in locale_lines if line.startswith('singularity.objective.cooldown='))
        rendered = ''.join(scene_text[f'{language}-singularity-cooldown-{width}'])
        assert re.sub(r'\s+', '', cooldown_text) in re.sub(r'\s+', '', rendered), 'Cooldown HUD must explain that both anchors are resetting'
        assert get('phase2')['boss_stage'] == '2' and 1 <= int(get('phase2')['boss_reboot']) <= 90
        assert get('phase2')['predicted_shots'] == get('phase2')['boss_warning'] == '0'
        assert get('victory')['state'] == 'VICTORY'
        assert get('victory')['platforms'] == get('victory')['hazard_count'] == get('victory')['controller_expose'] == '0'
        assert int(get('victory')['score']) > 0
        for name in names:
            row = get(name)
            assert row['hp'] == '100' and row['lab'] == 'false', (row['scene'], 'Safe fixture took damage or enabled Lab')
            assert row['flashes'] == ('false' if language == 'en' else 'true')
            assert 0 <= int(row['hazard_count']) <= 1
for name in expected:
    assert (out / (name + '.png')).is_file(), name
print('52 Singularity captures passed: bilingual UI, reduced-flash English, three actual echoes, safe E zones, fixed platforms, one-time reward, dual-anchor resonance and cooldown, locked volley, phase reset and final victory.')
