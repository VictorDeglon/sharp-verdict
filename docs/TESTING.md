# Testing record

What was run for each version, and what was not.

| Version | Unit tests | Mutation checks | Real-game test |
| --- | --- | --- | --- |
| 0.2.0 | 261 passed, 0 failed | Listed in the README | Not run by us. The source says it was run before delivery. |
| 0.3.0 | 280 passed, 0 failed | Ignoring the tier handicap broke 4 tests. Letting the lock switch targets broke 7. | **Not run.** Minecraft launched and the mod loaded, then the sandbox could not open a graphics context. The scenario for tiers and the no-switch lock was written but has not been executed. |
| 0.4.0 | 314 passed, 0 failed | Removing the fall blend broke 3 tests. Removing the reach hold broke 1. Removing the slew limit broke 4. | **Not run**, same reason. Two checks were changed for the new out-of-reach aim (middle of the body instead of the feet). |
| 0.4.1 | 314 passed, 0 failed | None needed: no behaviour change. The cached ignore list is in `GameAccess`, which touches Minecraft, so it has no unit test. | **Not run**, same reason. |
| 0.5.0 | 339 passed, 0 failed | Six deliberate bugs in the nearest-point code, each caught: the point collapsing to the middle (6 tests failed), the picker ignoring the preferred point (6), no inset (4), the planner never leaning in (8), no slew on the weight (4), the controller never passing the point (4). | **Not run**, same reason. No in-game check was added for the nearest point. |

Each mutation check edits the code to be wrong on purpose, runs the suite, and confirms at least one test fails. The edit is then reverted. A suite that cannot fail proves nothing.

## Build verification (0.4.1)

- `gradle/wrapper/gradle-wrapper.jar` is byte-identical to the jar Gradle 9.7.1 generates itself (same SHA-256).
- `./gradlew build` was run in a clean copy of the tree, with no `build` or `.gradle` directories: 314 tests passed and the jar was produced.
- GitHub Actions built it from an empty cache on a fresh runner (Java 25.0.3, Microsoft build) and passed all 314 tests, for both the push and the pull request run. The sandbox this was written in could not do that itself: Maven Central answered it with HTTP 429.

## What is covered by unit tests

Activation and release, target selection and tiers, the lock that does not move, aim point and aim height (out of reach, in reach, falling), camera maths and limits, hitbox geometry and colours, the status text, the config and its migration, and the ignore list.

## What is not covered by anything yet

- The camera on a real multiplayer server, with real latency and other players.
- A raised shield, elytra, bots from fake-player mods.
- How the camera feels on a real GPU and a real mouse.

Run `./gradlew runClientGameTest` (on Linux, `xvfb-run -a ./gradlew runClientGameTest`) on a machine with working OpenGL before trusting the in-game behaviour.
