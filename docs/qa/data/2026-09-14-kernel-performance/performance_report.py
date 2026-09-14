from pathlib import Path
import csv,json,math
from collections import Counter
root=Path('build/kernel-verification')
summary={}
for label in ('before','initial','after'):
    folder=root/('performance-'+label)
    rows=list(csv.DictReader((folder/'samples.csv').open(encoding='utf-8')))
    cases={}
    for name in sorted({r['case'] for r in rows}):
        values=[r for r in rows if r['case']==name]
        metrics={}
        for column in ('simulation_publish_ms','edt_paint_ms','edt_dispatch_ms'):
            samples=sorted(float(r[column]) for r in values)
            metrics[column]={'p50':samples[math.ceil(len(samples)*.5)-1], 'p95':samples[math.ceil(len(samples)*.95)-1], 'max':samples[-1]}
        for row in values:
            assert row['published']=='true'
            high='_high_' in name
            assert int(row['player_bullets'])==(320 if high else 32)
            # Counts are sampled AFTER the real update. Ordinary shooters may fire,
            # and the fourth world may spawn an ambient hostile in normal cases.
            assert int(row['enemy_bullets'])==180 if high else 12 <= int(row['enemy_bullets']) <= 13
            assert int(row['particles'])==(1200 if high else 120)
            assert int(row['enemies'])==40 if high else 6 <= int(row['enemies']) <= (7 if name.startswith('world4_') else 6)
        populations={column:dict(sorted(Counter(int(r[column]) for r in values).items()))
                     for column in ('enemies','player_bullets','enemy_bullets','particles')}
        cases[name]={'samples':len(values),'post_update_populations':populations,**metrics}
    assert len(cases)==12
    summary[label]={'total_samples':len(rows),'warmup_ms':750,'sample_window_ms':1750,'cases':cases}
(root/'performance-comparison.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
lines=['| 场景 | 旧包 p50 / p95 | 新版初稿 p50 / p95 | 缓存后 p50 / p95 | 缓存后 EDT p95 |','| --- | ---: | ---: | ---: | ---: |']
for name in sorted(summary['after']['cases']):
    cells=[]
    for label in ('before','initial','after'):
        t=summary[label]['cases'][name]['simulation_publish_ms']; cells.append(f"{t['p50']:.2f} / {t['p95']:.2f}")
    edt=summary['after']['cases'][name]['edt_paint_ms']['p95']
    lines.append('| '+name+' | '+' | '.join(cells)+f' | {edt:.2f} |')
(root/'performance-table.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('\n'.join(lines))
print('Samples:', {k:v['total_samples'] for k,v in summary.items()})
