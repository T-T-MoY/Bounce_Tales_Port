# Reverse-engineering notes

## Phase 4: menu, progression, HUD and audio

- `ResourceIndex.LEVEL_OBJECTS` (`n.v` in the original) is the authoritative level order:
  `bf, bg, bh, bi, br, bj, bk, bl, bm, bs, bn, bo, bp, bq, bt, bu`. `LevelCatalog` reads it
  once, filters names without `levels/<name>.json`, and falls back to the same literal order.
- The menu opens by default; `-Dbounce.level=<name>` still launches a level directly. Automated
  screenshots (`bounce.screenshot`/`bounce.frames`) bypass the menu unless `bounce.menu=true`,
  and the menu ignores input in capture mode so it cannot start a level by accident.
- Music follows `n.d()`: menu `28`, level groups `29/30/31`, alternate themes `32`/`35` on the
  fourth/fifth level of each group, death sound `33`, level-complete jingle `34`.
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
- The death state originally had a provisional red overlay; Phase 5 replaced it with the real 3 s bounce/fall animation (see below).

## Phase 5: ball forms, AFK faces and death animation

- `c.f34d` is the shape index (`0` normal, `1` marble/stone, `2` striped beach ball). `c.f()`
  advances it on every shape pickup (`n.java` case 20 -> `f201a.f()`), wrapping to `0`; the port
  exposes `BallBody.cycleType()` and unlocks all three for testing (`1`, `2`, `3`, `TAB`).
- Shape physics comes from the per-index tables in `c.java`: mass `{1.0, 1.4, 0.5}`, radius
  `{20, 20, 20}`, roll resistance `{0.6, 0.3, 0.6}`, acceleration `{500, 350, 312.5}`, max
  speed `{280, 350, 150}`, air divisor `{1.5, 2.5, 1.2}`, jump `{288, 255, 162.5}`. Because the
  original divides forces and kicks by the current mass, the port keeps one `BallBody` and
  selects the table entry from `type` each step.
- Shape rendering (`c.a(Graphics, ...)`): normal ball = black outline + red fill + sprites
  `17`/`28`; stone = 32 pre-rendered rotations `c.f9a` indexed by `31 - degrees/11.25`; beach
  ball = six 60-degree coloured sectors (`c.p` = `EDEDED, 0064BC, EDEDED, E11900, EDEDED,
  E8DF05`) rotated by the roll angle, an offset white core, and sprite `5`.
- AFK state machine (`c.java` 335-368): when no key is held and both speed components are below
  `40`, `f6b` counts down from `3000` ms. On expiry `f7c = 2` (blink, `s = 600` ms); afterwards
  the game picks `f7c` in `3..8`, using `600` ms for `3` and `1500` ms for the rest, and
  restarts the wait. Any input or motion resets `f7c = 0` and `f6b = 3000` (also done in
  `n.java` 1313/2332). Sprite mapping: `462` (blink animation), `447` (wake), `452-460`
  (`c.f8c[f7c-4]`), drawn on top of the current ball.
- Death (`c.e()` 848-858 and `n.java` 1371-1394): entering state `1` plays sound `33`, switches
  `g.a[0]` to `3` and sets `f129c = 3000`. The first `1000` ms use
  `y = f151b[(i/5) % 360]`; after that the ball falls by `(i-1000)*22500/65536` px. Face `20`
  is drawn while `g.a[0] == 3`. When `f129c` reaches zero the level restarts (ball, camera and
  `f7c` reset). The port reproduces the same 3 s timing with `DEATH_BOUNCE_TIME`,
  `DEATH_FALL_PER_MS` and a generated `f151b` table (`n.m30a()` recurrence `v -= p/57;
  p += v/57`).
- `f151b` indexing note: the original table is 360 entries of `sin` scaled by ~360, and the port
  divides the sampled value by `4` before adding it to the death position, matching the pixels
  seen in the capture.
- Sound routing fix: `33.ogg` is the death sound (`p.m68c(33, 1)`) and `34.ogg` the
  level-complete jingle (`p.m68c(34, 1)`); earlier notes had them swapped.
- Menu audio options: `SoundBank.musicMuted`/`fxMuted` are static so the menu choice survives
  the screen switch; `M` toggles music (volume 0 while keeping the track playing) and `F`
  toggles effects. `MenuScreen` shows `M: musica ON/OFF  F: efectos ON/OFF` in red when muted.
- Verification: `gradlew build` succeeds; `-Dbounce.shape=1|2` captured the stone and beach
  balls, `-Dbounce.face=2` the blink overlay, a 400-frame AFK run logged `AFK: cara 2/3/4`,
  `-Dbounce.die=true` captured the death face and a 220-frame run logged `nivel reiniciado`.
  System-level key injection is blocked in this environment, so `-Dbounce.menuKeys=MF` feeds
  the same `MenuScreen.handleKey(...)` path used by real key presses; the log shows
  `musica silenciada` and `efectos silenciados`, and the capture shows both options as `OFF`.

## Phase 6: water and breakable walls

- The level exporter labels tag 10 as `"beam"`; it is the original `i` water/current entity.
  Visible water now renders as a translucent blue body with a sampled, wavy surface; black-color
  current volumes remain invisible. The player ball renders at depth 8 while inside visible water.
- `Water.apply` marks `BallBody.inWater`/`touchedWater`, applies interpolated current and mass-based
  damping, and the physics trace logs water transitions plus net force. In `bg`, the beach ball
  (mass 0.5) oscillated at world Y≈607 at its center (the water surface is ≈587); normal and stone
  descend to the bottom geometry (normal settled around Y≈541 in the 10-second run).
- Trigger opcode 1 starts decoration animations: 334→474 (5 frames, 750 ms, then 1500 ms fade and
  hide), 118→485 and 342→480 (loops). Opcode 29 marks collision geometry dirty when `solid`
  changes; `CollisionWorld` rebuilds it on the next resolve. Trigger vars 3-6 are refreshed after
  physics/interactions with ball type, speed, `vx`, and `vy`.
- Breakable-wall smoke test:
  `gradlew.bat desktop:run -Dbounce.level=bm -Dbounce.teleport=1580,-560 -Dbounce.auto=right -Dbounce.shape=1 -Dbounce.trace=true`
  logged trigger 4 activating decoration 6 and the ball crossing past the wall (world X≈1842).
- Water smoke tests:
  `gradlew.bat desktop:run -Dbounce.level=bg -Dbounce.teleport=-800,680 -Dbounce.shape=2`
  and the same command with `-Dbounce.shape=0` or `1`. The beach-ball run logged repeated
  water entry/exit around the surface; the other shapes sank to the bottom. Captures:
  `out/phase6_wall.png`, `out/phase6_water_beach.png`, `out/phase6_water_normal.png`, and
  `out/phase6_water_stone.png`.
- Verification: `gradlew.bat build --console=plain` succeeds; all four desktop smoke runs
  generated screenshots successfully.

## Phase 7: powered tubes

- Corrected tag 11 in `tools/bounce_levels.py`: it is the original `l` tube entity, whose payload
  contains a target-object index and signed launch power. The loader accepts both the corrected
  `"tube"` payload and legacy `"checkpoint"` JSON.
- `Tube` captures only its configured ball when the swept ball position intersects the local
  80x80 trigger area. It waits for the fire input, allows left/right aiming while held, then runs
  a 900 ms animation and launches at the 600 ms point from local X=120. Velocity uses the tube's
  world orientation and power; the cooldown is 500 ms.
- Normal ball physics, collision resolution and other interactions are skipped while the tube
  holds the ball. A simple oriented tube body, nozzle and charge indicator are rendered.
- The runtime logs capture and launch events, including the resulting launch velocity.
- Verification: `gradlew.bat build --console=plain` succeeds. In `bn`, `auto=jump` logged entity
  3 capturing target 188 and launching at `(800, 0)`; `auto=rightjump` changed the aim and launched
  at `(799, -40)`. `out/phase7_tube.png` records the aimed run.
