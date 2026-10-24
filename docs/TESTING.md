# Testing record

What was run for each version, and what was not.

| Version | Unit tests | Mutation checks | Real-game test |
| --- | --- | --- | --- |
| 0.2.0 | 261 passed, 0 failed | Listed in the README | Not run by us. The source says it was run before delivery. |
| 0.3.0 | 280 passed, 0 failed | Ignoring the tier handicap broke 4 tests. Letting the lock switch targets broke 7. | **Not run.** Minecraft launched and the mod loaded, then the sandbox could not open a graphics context. The scenario for tiers and the no-switch lock was written but has not been executed. |
| 0.4.0 | 314 passed, 0 failed | Removing the fall blend broke 3 tests. Removing the reach hold broke 1. Removing the slew limit broke 4. | **Not run**, same reason. Two checks were changed for the new out-of-reach aim (middle of the body instead of the feet). |

Each mutation check edits the code to be wrong on purpose, runs the suite, and confirms at least one test fails. The edit is then reverted. A suite that cannot fail proves nothing.

## What is covered by unit tests

Activation and release, target selection and tiers, the lock that does not move, aim point and aim height (out of reach, in reach, falling), camera maths and limits, hitbox geometry and colours, the status text, the config and its migration, and the ignore list.

## What is not covered by anything yet

- The camera on a real multiplayer server, with real latency and other players.
- A raised shield, elytra, bots from fake-player mods.
- How the camera feels on a real GPU and a real mouse.

Run `./gradlew runClientGameTest` (on Linux, `xvfb-run -a ./gradlew runClientGameTest`) on a machine with working OpenGL before trusting the in-game behaviour.
