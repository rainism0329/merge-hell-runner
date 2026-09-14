# Follow-up qualification

The six earlier runs used fixed layout, encounter, drop and upgrade seeds, but the probe initially omitted resetting Player's independent combat RNG. The observed traversals are real, but their critical-hit stream cannot be replayed exactly. The probe now calls Player.setCombatSeed(seed) in its chapter-entry fixture, matching the production new-run seed API.

The base chapter 4 follow-up is preserved separately:

- `../playthrough-base4-seeded-followup/`: original bot policy, all RNG streams fixed, explicitly extended 14,000-step budget. The bot stopped on a ledge at x7235 while repeatedly jumping at ground enemies below it; HP48, 2 lives and 3 bombs remained. This is the bot's lack of ledge traversal planning, not a proven game softlock.
- `../playthrough-base4-bomb-followup/`: same seed and base build; the bot additionally presses the ordinary B button after 600 ticks of stalled combat, consuming available bombs. It reached the real BOSS_FIGHT at 6,172 steps, with HP50, 3 lives and 0 bombs. No LAB, damage/HP injection or traversal teleport was used.

These probes stop at the boss fight. They do not establish human difficulty or prove that the bosses can be defeated with these remaining resources.
