# CLAUDE.md

SHARP: Verdict is a Fabric client mod for Minecraft 26.3. Hold a key, it locks one target, outlines its hitbox, and can steer the camera onto it. It is the target lock module of the SHARP client. The mod id is `sharp` and the Java package is `dev.sharp`. Keep the id: config and keybinds depend on it.

## Commands

```bash
./gradlew build                 # needs JDK 25. Compiles, runs every unit test, builds build/libs/sharp-<version>.jar
./gradlew test                  # unit tests only, a few seconds
./gradlew runClient             # development client with the mod loaded
./gradlew runClientGameTest     # plays the feature in real Minecraft. Needs working OpenGL. Linux: xvfb-run -a ./gradlew runClientGameTest
```

The first build downloads Minecraft and the mappings. If you build somewhere that only has Java 21, download Temurin 25 and Gradle 9.7.1 rather than fighting the wrapper. Gradle 9.7.1 is what `gradle-wrapper.properties` pins. In the cloud sandbox this was written in, the game launches but cannot open a graphics context, so `runClientGameTest` cannot run there. Say so when you could not run it. Do not report it as passing.

## Layout

```
src/main/java/dev/sharp      pure Java, no Minecraft imports, all unit tested
  core/math                  V3, Box, AimMath (Minecraft yaw/pitch conventions)
  core/activation            ActivationTracker: the hold-to-lock state machine
  core/targeting             TargetSelector, TargetLock, AimPointPicker, AimHeightPlanner, IgnoreList, TargetTier, settings
  core/camera                AimController: easing, speed and acceleration limits, yielding to the mouse
  core/render                hitbox geometry, colour policy, interpolation (BoxTrail), mesh builder
  core/hud, core/debug       status text, debug panel
  config                     SharpConfig, ConfigStore (JSON, validated, versioned)
  controller                 SharpController: runs one frame of all of the above
src/client/java/dev/sharp/client   the only code that touches Minecraft
  GameAccess                 every read of game state lives here
  SharpRuntime               per-frame and per-tick glue, error containment
  SharpClient, InputHandler, CameraController, HitboxRenderer, CueOverlay, DebugOverlay, ConfigManager
src/gametest                 client game test
docs/                        API_NOTES.md (which game calls were verified), TESTING.md (what was run per version)
assets/branding, tools/branding   logo, icon, banner and the script that generates them
```

Frame flow: key, focus, screen and world state go through `ActivationTracker`. `GameAccess.candidates` feeds `TargetSelector` and `TargetLock`. The target goes to `AimHeightPlanner` and `AimPointPicker` for the aim point. `AimController` turns that into a camera step, and the scene goes to the renderer.

## Rules for changes

- **Keep `src/main` free of Minecraft imports.** Anything that needs the game goes through `GameAccess` and is handed to the core as plain data. That is what makes the logic testable.
- **A behaviour change is a test change.** Add or update tests with it. The suite is the only thing that checks the camera maths without a GPU.
- **Mutation-check new logic.** Break it on purpose (flip a sign, drop a check), run the suite, confirm a test fails, revert. A test that cannot fail proves nothing. Record the result in the README's Tests section.
- **Game API calls are tagged.** In `GameAccess`, mark a call `VERIFIED` only when upstream code that compiles against 26.3 uses it. Check class and method names against the 26.3 jar with `javap` before writing them, and update `docs/API_NOTES.md`.
- **Config.** New settings are public fields with a default in `SharpConfig`. Clamp and clean them in `sanitize()`. It must be idempotent, and `SharpConfigTest.defaultsAreAlreadyValid` must keep passing. Missing keys keep their default. Bump `CURRENT_VERSION` only when an old file would keep the old behaviour, and add what survives to `migratedFrom`.
- **Docs follow the code.** Update the README (settings table, behaviour, the test count in the badge and the Status note) and `docs/TESTING.md` in the same change.
- **Writing style for docs and release notes.** Plain and direct. Short sentences. No em dashes, no filler, no hype. State what was not tested.

## Design decisions to keep unless asked

- **The lock never moves to a second entity during a hold** (`TargetLock`). If the target is lost the lock goes quiet and can only resume on the same entity. Release and press again to pick another.
- **Ranking is distance plus a handicap per tier plus a mild angle term** (`TargetSelector`). Players, then armor stands, then everything else. `priorityTiers=false` restores crosshair-first ranking.
- **The aim is deterministic.** No random jitter in the aim point, the speed or the timing. `AimHeightPlannerTest.theSameInputsAlwaysGiveTheSameOutput` pins this. Smoothness comes from easing and limits, not noise.
- **The mod is not stealth.** Do not add behaviour whose purpose is to hide from a server's anti-cheat. The README says it does not hide itself, and that is meant literally.
- **Smoothing keys off a client tick counter** (`SharpRuntime`), not `level.getGameTime()`. A server resends game time and it jumps, which used to restart the interpolation and hitch the aim point once a second.
- **Out of reach aims at the middle, in reach at the feet, falling at the middle sliding down** (`AimHeightPlanner`), slew limited to 2.5 box heights per second.
- **The ignore list** (`SharpConfig.DEFAULT_IGNORED_TYPES`) only lists types by id. Armor stands are deliberately not on it: they are the second tier.

## Releasing

Version lives in `gradle.properties` (`mod_version`). One commit per version, tagged `vX.Y.Z`. Each GitHub release carries the jar, a plain text list of test results from that build, and notes that say what was and was not run. The social preview image cannot be set through the API: upload `assets/branding/social-preview.png` under Settings by hand.

## Known gaps

- The in-game test has not been re-run since the 0.2.0 delivery. The scenarios for tiers, the lock that does not move, and the 13-block out-of-reach aim exist in `SharpGameTest` but have never been executed.
- Nothing has run on a real multiplayer server.
- Raised shields, elytra and fake-player bots are unit tested only.
