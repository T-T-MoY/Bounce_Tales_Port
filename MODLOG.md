# Reverse-engineering notes

## Phase 4: menu, progression, HUD and audio

- `ResourceIndex.LEVEL_OBJECTS` (`n.v` in the original) is the authoritative level order:
  `bf, bg, bh, bi, br, bj, bk, bl, bm, bs, bn, bo, bp, bq, bt, bu`. `LevelCatalog` reads it
  once, filters names without `levels/<name>.json`, and falls back to the same literal order.
- The menu opens by default; `-Dbounce.level=<name>` still launches a level directly. Automated
  screenshots (`bounce.screenshot`/`bounce.frames`) bypass the menu unless `bounce.menu=true`,
  and the menu ignores input in capture mode so it cannot start a level by accident.
- Music follows `n.d()`: menu `28`, level groups `29/30/31`, alternate themes `32`/`35` on the
  fourth/fifth level of each group, level-complete jingle `33`. `34` is the game-over jingle.
- MIDI conversion: `tools/MidiToOgg.java` renders each `.mid` offline with the JDK Gervill
  `SoftSynthesizer` to 44.1 kHz stereo WAV; `tools/convert_midi.py` then encodes it to Vorbis
  with ffmpeg. All eight tracks produced `assets/sounds/28.ogg`-`35.ogg`; `33.ogg` was checked
  as audible (3.296 s, peak about -3.2 dB).
- `SoundBank` streams `Music` for looping tracks and falls back to a logged no-op when a file is
  missing (the original synthesised SFX ids 2, 8 and 56-88 have no asset yet). Disposing a
  screen stops and releases its music.
- The HUD uses collectible sprite `208` as the counter icon, spells the level as
  `NIVEL <n> <name>`, and keeps a shadowed `collected/total` counter plus a victory banner.
- Victory waits 2.2 s while the jingle plays, then `BounceTalesGame.levelCompleted` advances to
  `LevelCatalog.next(...)`; the last level returns to the menu. `-Dbounce.autocomplete=true`
  sets `vars[0] = 2` after load as a deterministic progression test.
- Desktop verification: `gradlew.bat build` succeeds; menu, `bf` and `bg` screenshots render
  correctly; the autocomplete run chained `bf -> bg -> ... -> bu` and logged each transition.

## Pad and camera behavior

- `resources/e.class`: the pad advances frames only during an active compression; at rest its frame is `0`. Level `bf` uses sprite `497`, a six-frame animation (`498`-`503`). `e.d()` holds the ball until `duration - duration / frameCount`, applies the stored kick, and then finishes the animation.
- `c.java` case 8: duration uses impact speed clamped to `100..1000` and is `((1100 - speed) >> 1) + 20` ms. Kick uses the actual falling speed and is capped at `400 * strength * 2 / 100`.
- `resources/k.class`: `b(false)` recalculates the camera focus and advances the spring; `b(true)` snaps it. When a camera opcode targets the ball entity, the port now uses the simulated ball position rather than that entity's static spawn transform.
- `bf.json`: trigger `99` deletes enemy entity `68` (type `2`) after its timed sequence. The type-2 renderer draws sprite `504`; while its reveal state is active it overlays sprite `189` or `194` at the original 61 px clipped reveal height. The visible phase keeps the full reveal; the hidden phase fades it out over 800 ms.
- `c.java` case 10 routes type-2 contacts to `m.e()`, `m.c()`, or `m.b()`; unlike the patrol case, the mole is not killed. `m.b()` applies a 100 px/s horizontal push while visible, or a 200 px/s horizontal plus 400 px/s upward kick when emerging from buried state, with a 500 ms cooldown.
- Desktop verification with `-Dbounce.teleport=1300,620` showed the type-2 reveal before trigger `99` completed; its runtime log confirmed the timed trigger completed.
- The death state has a short red screen overlay; a desktop capture with the ball teleported below the level confirmed the effect.
