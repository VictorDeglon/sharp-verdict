# Changelog

Each version is one commit, tagged `vX.Y.Z`. Download jars from [Releases](https://github.com/VictorDeglon/sharp-verdict/releases). What was run for each version is in [docs/TESTING.md](docs/TESTING.md).

## 0.4.0

Rebranded as **SHARP: Verdict**, the target lock module of the SHARP client. The mod id stays `sharp`, so your config and keybinds carry over.

- **Ignore list.** `ignoredEntityTypes` lists entity types that are never targeted: end crystals, item frames, paintings, vehicles, projectiles, villagers, golems and more. `*` wildcards work. `ignoreOwnPets` skips your own tamed wolves, cats and parrots. Anything the game's crosshair cannot pick is skipped too.
- **Aim height adapts.** The middle of the body while the target is out of reach, the feet once it is in reach, and a slide down while it falls. Changes are slew limited, so the aim never jumps. Nothing is random. `aimAdaptive` turns it off.
- **New look.** Black and white with one red point: logo, mod icon, README banner and social preview, with `tools/branding` to regenerate them.
- **Docs.** The README was rebuilt around install, controls and a first-lock walkthrough, with the long form folded into a reference.

314 unit tests pass. The real-game test has not been re-run for this version, see [docs/TESTING.md](docs/TESTING.md). Nothing has run on a real multiplayer server yet.

## 0.3.0

- **Runs everywhere.** The single-player and LAN gate is gone. Selection, outlines and the camera work on servers too.
- **Priority tiers.** Players, then armor stands, then animals, ranked by distance with a handicap in blocks per tier (`priorityTiers`, `armorStandPenalty`, `otherPenalty`).
- **The lock does not move.** It never jumps to a second entity during a hold. Release and press again to pick another.
- **No name needed.** `requireMarker` is now optional and off by default.
- **No more once-a-second hitch on servers.** Smoothing keys off a client tick counter instead of the world clock, which jumps when a server resends the time.
- Spectators and dying entities are skipped.

280 unit tests pass. The real-game test was not run for this version.

## 0.2.0

Multiplayer visualization patch. Selection and hitbox outlines run on servers for marked entities. The camera stays single-player only.

261 unit tests pass.
