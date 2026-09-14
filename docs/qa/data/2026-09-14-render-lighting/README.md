# Static panorama lighting optimization — 2026-09-14

The only changed production class in this comparison is `IndustrialArt`. The after run prepends its independently compiled class to the unchanged previous production and test classpath. KernelWorldRenderer and TraversalRenderer are unchanged. This is an isolated change comparison, not the full fifth-world release benchmark.

The existing normal and mirrored city panoramas remain available for nonstandard composites, clipping, scaling and subpixel target transforms. Two additional fixed 1200×600 RGB buffers bake the exact existing world tint, shadow and vertical gradient. The same buffers are repainted on a light change; camera positions never create cache entries. Additional retained raster storage is 5,760,000 bytes (5.49 MiB); all four panorama buffers total 11,520,000 bytes (10.99 MiB). Normal Kernel and Blueprint gameplay uses the level-0 light already prepared by construction, so moving between these scenes does not reprepare the cache.

## Pixel and regression checks

The isolated reference class is the original renderer renamed without behavior changes. `BackdropProbe.java` compared 200 render targets, including every world tint, palette revisits, negative/large/fractional camera positions, reflected panorama boundaries, three widths and eight nonstandard graphics/height cases: **zero differing pixels**. The two included unedited PNG files are byte-identical. The retained screenshot is one backdrop rendering, not a gameplay screenshot.

Two focused JUnit tests also passed: cached and original composition paths match over 56 camera/palette/viewport cases, and repeated palette changes retain the same bounded raster identities. `pixels.log` and `tests.log` preserve their outputs.

For a continuously moving camera, the isolated backdrop uses 150 warmup draws followed by 400 measured draws into a 960×600 ARGB_PRE target. Before p50/p95: **3.5504 / 3.7318 ms**; after: **0.2562 / 0.3599 ms**. No scenery or motion is removed.

## Production callback measurements

Both runs use Java 17.0.12, headless mode, 256 MiB initial / 512 MiB maximum heap, and the same Java Flight Recorder profile setting. The unchanged compiled KernelPerformanceProbe uses 750 ms warmup and 2000 ms sample windows per case. Its committed source was subsequently adjusted to 1750 ms for the separate release benchmark; the actual metadata in these preserved summaries is authoritative for this pair.

Synthetic load creation is outside callback timings. Simulation, collisions, rendering, frame publication, EDT dispatch and paint are real production paths. Each heavy-load sample retained exactly 40 hostiles, 320 player bullets, 180 enemy bullets and 1200 particles. Normal load starts at 6/32/12/120; ordinary simulation can add a hostile or enemy bullet. Every measured frame published, and all twelve mailboxes closed with zero retained raster buffers. The CSV keeps actual entity counts and EDT timings for every sample.

These are unpaced callback timings, **not FPS or a native IDE playthrough**. Machine load changed during the pair: unchanged world 2 controls varied by approximately 0–1.4 ms. No claim is made that the observed absolute deltas are stable on every machine. World 4 improved in all six measured configurations.

| Case | Before p50 / p95 (ms) | After p50 / p95 (ms) | Samples before / after |
| --- | ---: | ---: | ---: |
| world2_normal_960 | 7.8831 / 9.6763 | 7.8217 / 9.4224 | 181 / 181 |
| world2_normal_600 | 6.8701 / 7.9961 | 7.3924 / 8.6624 | 184 / 173 |
| world2_normal_1280 | 6.8273 / 7.7225 | 7.3074 / 8.2026 | 104 / 101 |
| world2_high_960 | 13.8211 / 15.3830 | 14.4177 / 17.2452 | 113 / 106 |
| world2_high_600 | 13.0094 / 14.6158 | 13.8238 / 15.2610 | 111 / 105 |
| world2_high_1280 | 13.0604 / 14.6398 | 14.4495 / 17.5101 | 75 / 67 |
| world4_normal_960 | 12.2691 / 13.8141 | 8.8660 / 11.2290 | 125 / 162 |
| world4_normal_600 | 12.6543 / 13.9212 | 8.9731 / 9.7799 | 114 / 152 |
| world4_normal_1280 | 12.0122 / 13.2333 | 8.4374 / 9.4058 | 79 / 95 |
| world4_high_960 | 18.4391 / 20.9385 | 16.0980 / 18.4223 | 86 / 98 |
| world4_high_600 | 18.2743 / 20.1649 | 16.0173 / 17.9272 | 83 / 93 |
| world4_high_1280 | 18.5795 / 21.4176 | 15.2688 / 16.7852 | 60 / 69 |
