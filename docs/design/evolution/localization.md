# Game language contract

The game supports English (`en`) and Simplified Chinese (`zh-CN`). Each published frame
uses one language. Menu actions, HUD, upgrade descriptions/effects, world objectives,
radio dialogue, Boss states, floating combat text, logs, settlement and error screens
share that choice. Physical keycap names, source-code syntax and the IDE plugin identity
remain literal; they are not alternative-language instructions.

## Resources and presentation

`src/main/resources/game/i18n/messages_en.properties` and `messages_zh-CN.properties`
are UTF-8 files with matching semantic keys and numbered placeholders. The first
iteration contains 505 entries per language. Do not apply Java Properties' historical
Latin-1 reader to these files.

For new UI, address a resource key with `GameText.message(key, arguments...)`, then use
`GameText.draw` to draw it. Existing simulation/controller contracts retain canonical
English messages and names. `GameText` has an exact source index plus anchored templates
to adapt those existing strings at the presentation boundary. This allows retained log
entries, in-flight combat text and the current radio line to switch language immediately
without restarting their timers or translating the simulation state. It is not a global
search-and-replace over the game state. Numeric facts, enum IDs, asset names and save IDs
are not translated.

Templates are compiled once, with bounded per-thread/per-language caches and recursive
argument resolution. Ambiguous unit and weapon-level slots require numbers: `[ L ]`
must remain a keycap, not become a weapon level. Regression tests cover that case,
numeric damage, code notation, nested upgrade/Boss names and literal replacement
characters. New templates must retain matching placeholder sets in both files and
should prefer specific wording over broad patterns.

`GamePanel.renderLogicalFrame` scopes the language to a complete simulation-owned frame.
The emergency error publication has its own scope. A scope restores the previous value
even after failure; different windows/threads cannot leak their language into each
other. This does not call `Locale.setDefault`. Numeric presentation uses `Locale.ROOT`,
so the selected game language does not inherit a conflicting decimal separator from
the operating system.

## Settings and old saves

Open settings with O from the menu or pause page. The ninth option is Language / 界面语言;
arrows select English or Simplified Chinese and Enter toggles the choice. The next
published frame uses the new language and the existing settings service saves it.

An empty or missing older `Settings.language` uses Chinese on a Chinese OS locale,
otherwise English. Invalid tags fall back by the same rule. After normalization the
settings copy contains `en` or `zh-CN`. The serialized bean default is deliberately
empty, so an explicit choice is saved even when it matches the current OS language;
moving that XML to another OS locale will not silently discard the preference.
Schema 2, checkpoint ownership, gameplay progression and the other settings retain
their existing contracts. Adding a third language also requires extending the selector.

## Layout and verification

Translate before measuring, wrapping, truncating or centering text. `GameText.wrap`
uses measured widths, English word boundaries and Unicode code points. Chinese
prefers Microsoft YaHei UI / Microsoft YaHei, then Noto Sans CJK SC / PingFang SC,
with the platform logical Dialog font as a final fallback. The game downloads no font.

The settings panel accommodates nine rows; menu details are concise enough for their
buttons in both languages. Upgrade cards have a larger text area, readable body fonts
and bounded wrapping. Tests verify the translated title, description and effect areas
for every upgrade, including entries that may not appear in one random draft.

`LanguagePreview` uses the production panel, input, frame publication and EDT paint.
It exports both languages at 960×600 and 600×400, plus the final strings passed to the
drawing helper. Chapter, position and result/error states are explicit QA fixtures;
these captures do not claim a normal playthrough. After exporting, run:

```powershell
E:/Python310/python.exe -B scripts/verify-languages.py
```

The check requires all 60 scenes, rejects Chinese text in English screens, and rejects
untranslated English UI words in Chinese screens. Its narrow exceptions are keycap
names and recognizable code snippets. Catalog parity, immediate switching, window
isolation, XML round trips, old-save fallback and measured wrapping are also covered
by Java tests. The error publication is exercised by the actual panel preview. See
the current [QA record](../../qa/2026-09-11-bilingual-interface.md).
