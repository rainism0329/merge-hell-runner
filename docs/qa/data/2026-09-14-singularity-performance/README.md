# Fifth-world release performance evidence — 2026-09-14

This compares the saved fourth-world plugin ZIP against the fifth-world production classes using the same `SingularityPerformanceProbe`. The old ZIP SHA-256 is `99f814ddd5a6476c11a89ac7ed3435758908404a458b50323d4b61268e19a8d1`. Its instrumented JAR supplies all before-run production classes and resources. After-run classes and resources come from the final local build; the summary records each classpath origin.

Both runs use Java 17.0.12 in headless mode, 256 MiB initial / 512 MiB maximum heap, 750 ms warmup and 1750 ms sampling per case. There is no JFR profiler in this pair. The probe covers worlds 2, 4 and 5, normal/full-budget load, and 600×400, 960×600 and 1280×800 physical windows: **18 cases per version**. The game renders its fixed 960×600 logical frame; the EDT scales that frame for the physical window. The 600×400 window has a 600×375 active viewport.

Synthetic actor/bullet/particle construction is excluded from callback times. Actual simulation, collision, rendering, frame publication and Swing painting run through production GameLoop/GamePanel/FrameMailbox paths. High-load frames retain exactly **40 hostiles, 320 player bullets, 180 enemy bullets and 1200 particles**. Normal loads begin at **6/32/12/120** and may gain an enemy or bullet through ordinary simulation; raw CSVs and `comparison.json` retain actual counts. Every sample published; all 18 mailboxes in each run closed with zero retained frame buffers.

Before samples: **1972**; after samples: **2260**. Raw CSV includes simulation + publish, EDT dispatch, EDT paint, and actual counts per frame. Summary files include setup latency, heap, non-heap, threads and GC measurements before/after every case and after disposal. The first setup includes cold asset/font initialization and is excluded from sampled timings. The preserved probe derives warmup/sample metadata directly from its nanosecond constants.

These are **unpaced callback timings, not FPS or a native IDE playthrough**. The new fifth world contains different scenery and interactions, so its before/after comparison combines new content and implementation changes. Background machine load can move the unchanged world 2 control; p95 or maximum excursions must not be interpreted as stable frame-rate guarantees. The separate [lighting-only comparison](../2026-09-14-render-lighting/README.md) isolates the static panorama optimization and checks pixel equality.

| Case | Simulation + publish before p50 / p95 (ms) | After p50 / p95 (ms) | EDT paint p95 before / after (ms) | Samples before / after |
| --- | ---: | ---: | ---: | ---: |
| world2_high_1280 | 13.1757 / 15.3198 | 12.9432 / 15.0635 | 10.7375 / 10.8843 | 75 / 75 |
| world2_high_600 | 13.5419 / 15.7845 | 12.9576 / 14.6662 | 2.7510 / 2.7317 | 106 / 111 |
| world2_high_960 | 13.7424 / 15.8668 | 13.4549 / 14.9089 | 1.8037 / 1.7420 | 112 / 115 |
| world2_normal_1280 | 6.7846 / 7.7591 | 6.6578 / 7.7738 | 10.6497 / 10.7698 | 104 / 104 |
| world2_normal_600 | 6.7817 / 8.2976 | 6.7737 / 7.9257 | 2.6178 / 2.7228 | 185 / 185 |
| world2_normal_960 | 8.1145 / 9.7889 | 8.0215 / 10.6878 | 1.8164 / 2.4346 | 175 / 173 |
| world4_high_1280 | 21.6967 / 34.5147 | 14.8596 / 16.3706 | 16.4789 / 10.6661 | 50 / 70 |
| world4_high_600 | 19.9553 / 23.2116 | 15.0270 / 17.3504 | 3.6668 / 2.7105 | 77 / 98 |
| world4_high_960 | 20.4567 / 25.6744 | 15.1226 / 16.7226 | 2.1547 / 1.7032 | 77 / 104 |
| world4_normal_1280 | 13.4495 / 18.3762 | 8.3285 / 9.4473 | 13.3268 / 10.6479 | 71 / 95 |
| world4_normal_600 | 13.0713 / 15.5469 | 8.4455 / 9.8170 | 3.2264 / 2.8211 | 109 / 157 |
| world4_normal_960 | 12.6549 / 14.5360 | 8.6196 / 10.8269 | 1.7454 / 1.7586 | 122 / 168 |
| world5_high_1280 | 13.7721 / 15.7602 | 12.9177 / 14.6180 | 10.5403 / 10.5666 | 73 / 75 |
| world5_high_600 | 14.0985 / 15.8783 | 12.9916 / 14.9280 | 2.7423 / 2.7576 | 104 / 111 |
| world5_high_960 | 14.0599 / 16.0246 | 13.2293 / 14.5375 | 1.6942 / 1.7181 | 111 / 117 |
| world5_normal_1280 | 8.1622 / 11.2820 | 6.4511 / 7.3743 | 13.4712 / 10.5583 | 90 / 106 |
| world5_normal_600 | 8.2224 / 11.4594 | 6.5377 / 7.7625 | 3.4048 / 2.8862 | 154 / 190 |
| world5_normal_960 | 8.1503 / 9.7859 | 6.8241 / 7.8713 | 1.7894 / 1.6363 | 177 / 206 |
