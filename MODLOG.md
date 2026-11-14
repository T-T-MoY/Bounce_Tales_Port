# Reverse-engineering notes

## Pad and camera behavior

- `resources/e.class`: the pad advances frames only during an active compression; at rest its frame is `0`. Level `bf` uses sprite `497`, a six-frame animation (`498`-`503`). `e.d()` holds the ball until `duration - duration / frameCount`, applies the stored kick, and then finishes the animation.
- `c.java` case 8: duration uses impact speed clamped to `100..1000` and is `((1100 - speed) >> 1) + 20` ms. Kick uses the actual falling speed and is capped at `400 * strength * 2 / 100`.
- `resources/k.class`: `b(false)` recalculates the camera focus and advances the spring; `b(true)` snaps it. When a camera opcode targets the ball entity, the port now uses the simulated ball position rather than that entity's static spawn transform.
- `bf.json`: trigger `99` deletes enemy entity `68` (type `2`) after its timed sequence. The type-2 renderer draws sprite `504`; while its reveal state is active it overlays sprite `189` or `194` at the original 61 px clipped reveal height. The visible phase keeps the full reveal; the hidden phase fades it out over 800 ms.
- `c.java` case 10 routes type-2 contacts to `m.e()`, `m.c()`, or `m.b()`; unlike the patrol case, the mole is not killed. `m.b()` applies a 100 px/s horizontal push while visible, or a 200 px/s horizontal plus 400 px/s upward kick when emerging from buried state, with a 500 ms cooldown.
- Desktop verification with `-Dbounce.teleport=1300,620` showed the type-2 reveal before trigger `99` completed; its runtime log confirmed the timed trigger completed.
- The death state has a short red screen overlay; a desktop capture with the ball teleported below the level confirmed the effect.
