"""Check final text captured by LanguagePreview; standard library only, no image editing."""
from pathlib import Path
import argparse
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('transcript', nargs='?', default='build/language-preview/displayed-text.txt')
args = parser.parse_args()

# Literal keycap names and recognizable code syntax stay invariant in the programming-themed world.
keycaps = {'SPACE', 'ENTER', 'ESC', 'SHIFT', 'Esc', 'Enter'}
code_lines = {
    'public void fix() {', 'return null;', 'throw new Exception();', 'git merge master',
    'System.exit(0);', 'if (bug) panic();', 'while(true) {', '>> HEAD', '<<< HEAD',
    '<<HEAD', 'NullPtr', 'undef', 'NaN', 'SIGSEGV', '404.jar',
}
scenes = set()
scene = ''
errors = []
for line in Path(args.transcript).read_text(encoding='utf-8').splitlines():
    if line.startswith('=== ') and line.endswith(' ==='):
        scene = line[4:-4]; scenes.add(scene); continue
    if not scene or not line.strip(): continue
    if scene.startswith('en-'):
        if re.search(r'[\u3400-\u9fff]', line): errors.append((scene, line))
    elif scene.startswith('zh-CN-'):
        if line in code_lines or re.fullmatch(r'0x[0-9A-F]+', line): continue
        words = re.findall(r'[A-Za-z]{3,}', line)
        if any(word not in keycaps for word in words): errors.append((scene, line))
expected = {'menu', 'settings', 'settings-background', 'settings-mute', 'heap-choice', 'heap-leak', 'upgrade', 'boss-impact', 'pause',
            'complete', 'repository', 'legacy', 'game-over', 'world-3-boss', 'world-4-boss',
            'world-5-boss', 'error'}
for language in ('en', 'zh-CN'):
    for name in expected:
        for width in (600, 960):
            scene = f'{language}-{name}-{width}'
            if scene not in scenes: errors.append((scene, 'Missing capture'))
if errors:
    for scene, text in errors: print(f'{scene}: {text}')
    raise SystemExit(f'Language audit failed: {len(errors)} findings')
print(f'Language audit passed: {len(scenes)} scenes, no mixed UI language or missing captures.')
