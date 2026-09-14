"""Validate and preserve the same 18-case probe before/after the fifth-world release."""
from pathlib import Path
import csv
import hashlib
import json
import math
import shutil

work = Path('build/singularity-verification')
out = Path('docs/qa/data/2026-09-14-singularity-performance')
expected = {f'world{world}_{load}_{width}' for world in (2, 4, 5)
            for load in ('normal', 'high') for width in (960, 600, 1280)}
rows = {}
summaries = {}
for label in ('before', 'after'):
    directory = work/f'performance-{label}'
    rows[label] = list(csv.DictReader((directory/'samples.csv').open(encoding='utf8')))
    summaries[label] = (directory/'summary.txt').read_text(encoding='utf8')
    assert {row['case'] for row in rows[label]} == expected, label
    assert all(row['published'] == 'true' for row in rows[label]), label
    assert 'warmup_ms=750 sample_window_ms=1750 per_case' in summaries[label], label
    assert 'closed_cases=18 expected_cases=18' in summaries[label], label
    assert 'all_eighteen_mailboxes_closed_with_zero_retained_buffers=true' in summaries[label], label
    for case in expected:
        selected = [row for row in rows[label] if row['case'] == case]
        assert len(selected) >= 20, (label, case)
        for row in selected:
            count = tuple(int(row[key]) for key in ('enemies', 'player_bullets', 'enemy_bullets', 'particles'))
            if '_high_' in case:
                assert count == (40, 320, 180, 1200), (label, case, count)
            else:
                assert count[0] >= 6 and count[1] == 32 and count[2] >= 12 and count[3] == 120, (label, case, count)

def quantile(selected, key, p):
    values = sorted(float(row[key]) for row in selected)
    return values[math.ceil(len(values)*p)-1]

metrics = {}
table = ['| Case | Simulation + publish before p50 / p95 (ms) | After p50 / p95 (ms) | EDT paint p95 before / after (ms) | Samples before / after |',
         '| --- | ---: | ---: | ---: | ---: |']
for case in sorted(expected):
    metrics[case] = {}
    for label in ('before', 'after'):
        selected = [row for row in rows[label] if row['case'] == case]
        metrics[case][label] = {'samples': len(selected)}
        for key in ('simulation_publish_ms', 'edt_dispatch_ms', 'edt_paint_ms'):
            metrics[case][label][key] = {f'p{int(p*100)}': quantile(selected,key,p) for p in (.5,.95)}
        metrics[case][label]['actual_counts'] = sorted({tuple(int(row[key]) for key in
                    ('enemies','player_bullets','enemy_bullets','particles')) for row in selected})
    def pair(label):
        timing = metrics[case][label]['simulation_publish_ms']
        return f'{timing["p50"]:.4f} / {timing["p95"]:.4f}'
    edt = ' / '.join(f'{metrics[case][label]["edt_paint_ms"]["p95"]:.4f}' for label in ('before', 'after'))
    samples = ' / '.join(str(metrics[case][label]['samples']) for label in ('before', 'after'))
    table.append(f'| {case} | {pair("before")} | {pair("after")} | {edt} | {samples} |')

out.mkdir(parents=True, exist_ok=True)
for label in ('before', 'after'):
    for name in ('samples.csv', 'summary.txt'):
        shutil.copyfile(work/f'performance-{label}'/name, out/f'{label}-{name}')
shutil.copyfile('src/test/java/com/bigphil/mergehell/SingularityPerformanceProbe.java', out/'SingularityPerformanceProbe.java')
shutil.copyfile(__file__, out/'performance_report.py')
before_hash = hashlib.sha256((work/'before-package.zip').read_bytes()).hexdigest()
result = {'before_package_sha256': before_hash,
          'warmup_ms':750,'sample_window_ms':1750,'expected_cases':18,
          'sample_totals':{label:len(data) for label,data in rows.items()},'cases':metrics}
(out/'comparison.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
(work/'performance-comparison.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
(work/'performance-table.md').write_text('\n'.join(table)+'\n',encoding='utf8')
readme = f'''# Fifth-world release performance evidence — 2026-09-14

This compares the saved fourth-world plugin ZIP against the fifth-world production classes using the same `SingularityPerformanceProbe`. The old ZIP SHA-256 is `{before_hash}`. Its instrumented JAR supplies all before-run production classes and resources. After-run classes and resources come from the final local build; the summary records each classpath origin.

Both runs use Java 17.0.12 in headless mode, 256 MiB initial / 512 MiB maximum heap, 750 ms warmup and 1750 ms sampling per case. There is no JFR profiler in this pair. The probe covers worlds 2, 4 and 5, normal/full-budget load, and 600×400, 960×600 and 1280×800 physical windows: **18 cases per version**. The game renders its fixed 960×600 logical frame; the EDT scales that frame for the physical window. The 600×400 window has a 600×375 active viewport.

Synthetic actor/bullet/particle construction is excluded from callback times. Actual simulation, collision, rendering, frame publication and Swing painting run through production GameLoop/GamePanel/FrameMailbox paths. High-load frames retain exactly **40 hostiles, 320 player bullets, 180 enemy bullets and 1200 particles**. Normal loads begin at **6/32/12/120** and may gain an enemy or bullet through ordinary simulation; raw CSVs and `comparison.json` retain actual counts. Every sample published; all 18 mailboxes in each run closed with zero retained frame buffers.

Before samples: **{len(rows['before'])}**; after samples: **{len(rows['after'])}**. Raw CSV includes simulation + publish, EDT dispatch, EDT paint, and actual counts per frame. Summary files include setup latency, heap, non-heap, threads and GC measurements before/after every case and after disposal. The first setup includes cold asset/font initialization and is excluded from sampled timings. The preserved probe derives warmup/sample metadata directly from its nanosecond constants.

These are **unpaced callback timings, not FPS or a native IDE playthrough**. The new fifth world contains different scenery and interactions, so its before/after comparison combines new content and implementation changes. Background machine load can move the unchanged world 2 control; p95 or maximum excursions must not be interpreted as stable frame-rate guarantees. The separate [lighting-only comparison](../2026-09-14-render-lighting/README.md) isolates the static panorama optimization and checks pixel equality.

'''+'\n'.join(table)+'\n'
(out/'README.md').write_text(readme,encoding='utf8')
manifest = {path.name:{'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
            for path in out.iterdir() if path.is_file() and path.name != 'manifest.json'}
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf8')
print(json.dumps({'cases':len(expected),'samples':result['sample_totals'],'evidence':str(out)},indent=2))
