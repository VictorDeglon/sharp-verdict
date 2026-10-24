# API notes: what was checked and what was not

Target: **Minecraft 26.3**, Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Loom 1.18-SNAPSHOT, Java 25, Gradle 9.7.1. These come from the official `FabricMC/fabric-example-mod` branch `26.3`. From 26.1 on, Minecraft ships unobfuscated, so code uses Mojang's own class names (`Minecraft`, `LocalPlayer`, `AABB`) and there is no mappings line.

## Status

Everything in `src/client` now **compiles against the real Minecraft 26.3** and the parts below marked "exercised" were seen working by the client game test (`./gradlew runClientGameTest`, see the README). The first version of this file was written before any of that was possible, from upstream Fabric sources only. That history is kept below because the "verified" column still tells you where each call came from.

One lesson from that first pass: a loose search marked `Minecraft.screen` as verified, but those hits were import lines (`...api.client.screen.v1...`). Upstream reads the current screen with `gui.screen()`, and the code uses that.

## Verified (appears in compiling upstream 26.3 code)

| Symbol | Used in | Evidence (path under the Fabric repos) |
| --- | --- | --- |
| `LevelExtractionEvents.END_EXTRACTION`, `LevelExtractionContext.deltaTracker()` | `SharpClient`, `SharpRuntime` | `fabric-rendering-v1/.../level/LevelExtractionEvents.java`, `LevelExtractionContext.java` |
| `DeltaTracker.getGameTimeDeltaPartialTick(boolean)` | `SharpRuntime` | docs `reference/latest/.../RenderingConceptsEntrypoint.java` |
| `LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN`, `LevelRenderContext.poseStack()`, `.submitNodeCollector()`, `.levelState().cameraRenderState.pos` | `HitboxRenderer` | `fabric-rendering-v1/.../LevelRenderEventsTests.java` |
| `submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> ...)` | `HitboxRenderer` | same test |
| `VertexConsumer.addVertex(pose, x, y, z).setColor(int)` | `BoxMesh` | `TestRenderUtils.java` |
| `PoseStack.pushPose / popPose / translate(double, double, double)` | `HitboxRenderer` | `LevelRenderEventsTests.java` |
| `HudElementRegistry.addLast(Identifier, HudElement)`, `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` | `DebugOverlay` | `fabric-rendering-v1/.../hud/` |
| `GuiGraphicsExtractor.text(font, String, x, y, color, shadow)`, `Minecraft.font`, `Font.lineHeight` | `DebugOverlay` | docs reference mods (`graphics.text(...)`) |
| `KeyMapping(String, InputConstants.Type, int, KeyMapping.Category)`, `KeyMapping.Category.register(Identifier)`, `KeyMappingHelper.registerKeyMapping`, `isDown()`, `consumeClick()`, `SDLScancode.SDL_SCANCODE_*` | `InputHandler` | `fabric-key-mapping-api-v1/.../KeyMappingsTest.java` |
| Lang key format `key.category.<namespace>.<path>` | `en_us.json` | that test mod's `lang/en_us.json` |
| `ClientTickEvents.END_CLIENT_TICK`, `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`, `ClientPlayConnectionEvents.JOIN / DISCONNECT` (lambda shapes) | `SharpClient` | `fabric-lifecycle-events-v1`, `fabric-networking-api-v1` sources |
| `Minecraft.getInstance()`, `.player`, `.level`, `.gui`, `.getConnection()`, `.getSingleplayerServer()` | `SharpRuntime`, `GameAccess` | many; `RecipeSyncClientTest` for the server call |
| `Minecraft.gui.screen()` | `SharpRuntime`, `CameraController` | `NetworkingChannelClientTest`, `ClientNetworkingImpl` |
| `Minecraft.gui.hud.setOverlayMessage(Component, boolean)` | `SharpRuntime` | `NetworkingPlayPacketClientTest` |
| `ClientLevel.entitiesForRendering()`, `Level.getGameTime()` | `GameAccess`, `SharpRuntime` | `ClientEntityLifecycleTests`, `NetworkingCommonTest` |
| `Entity.isRemoved()`, `.getId()`, `.getName().getString()`, `.getEyePosition()`, `.getYRot()`, `.getXRot()`, `.getTeam()` | `GameAccess`, `SharpRuntime` | `ClientGameTestTest`, `PlayerLookup`, `EntityEventTests`, `DebugApiTestClient`, `MultiPlayerGameModeMixin`, `FakePlayer` |
| `LivingEntity.isDeadOrDying()` | `GameAccess` | `fabric-entity-events-v1/.../LivingEntityMixin.java` |
| `HitResult.Type.MISS`, `Vec3` (constructor and `.x .y .z`), `AABB` (`.minX` ... `.maxZ`), `Component.literal`, `Identifier.fromNamespaceAndPath` | several | the test mods above |
| `FabricLoader.getInstance().getConfigDir()` | `ConfigManager` | `Indigo.java` |

## Calls no upstream source used, now compiled

Each is tagged `UNVERIFIED` in `GameAccess.java` from before the first compile. They all compile. The last column says whether the game test exercises them.

| Symbol | What it is for | Exercised in the real game |
| --- | --- | --- |
| `Minecraft#isWindowActive()` | Part of "cannot stay active after focus loss". | Yes, read every frame. |
| `IntegratedServer#isPublished()` | No longer used. It closed the feature in a world opened to LAN; there is no such gate now. | The LAN phase of the game test publishes the world and checks the feature still activates. |
| `Entity#isAlive()` | Skip dead entities and a dead local player. | Yes, every candidate scan. |
| `Entity#getCustomName()` | Practice marker by name. | Yes, the dummies and the mannequin are matched by it. |
| `Team#getName()` (via `Entity#getTeam()`) | Practice marker by team. | **No.** Compiles, never run. |
| `Entity#getBoundingBox()` | The hitbox to outline and aim at. | Yes, the mannequin's drawn box is compared with its real one. |
| `ClipContext(...)`, `Level#clip(ClipContext)` | Line-of-sight ray test against blocks. | Yes, `requireLineOfSight` is on in the test. |
| `Entity#turn(double, double)` and its `0.15` degrees-per-unit factor | Turning the camera the way the mouse does. | Yes, the camera reaches a dummy at the expected yaw. |

## Calls added with the colours, reach and status text

| Symbol | What it is for | Exercised in the real game |
| --- | --- | --- |
| `Player#isWithinAttackRange(ItemStack, AABB, double)` | Whether the held weapon can hit a box from where the player stands. | Yes: bare hands miss at 3.5 blocks, an iron spear hits. |
| `Entity#isPickable()`, `Entity#isSpectator()` | Which entities could intercept an attack (cover detection). | Yes, an armor stand in the way turns the target purple. |
| `LivingEntity#getYHeadRot()` | Which way a target faces, for the back of a shield. | Compiled and called for every target, the value is only used with a shield up. |
| `LivingEntity#isBlocking()` | Whether a target holds a shield up. | **Partly.** It is called for every target and returned false. The true branch needs something that raises a shield, which vanilla mobs do not. The shield maths itself is unit tested. |
| `Avatar` (player-shaped entities) | Match player-like bodies by their displayed name too. | The mannequin is matched, by its custom name. The displayed-name fallback is compiled only. |
| `Avatar` / `net.minecraft.world.entity.decoration.ArmorStand` (`instanceof`) | Sort a candidate into the player tier, the armor stand tier, or the rest. | Yes: the tier scenario locks a mannequin before a nearer sheep. Both class names were checked in the 26.3 jar. |
| `Entity#isSpectator()`, `LivingEntity#isDeadOrDying()` on candidates | Skip spectators and corpses, which matter on a server. | Compiled and called for every candidate; a spectator is not exercised. |
| `GuiGraphicsExtractor#fill`, `#guiWidth`, `#guiHeight`, `#pose()` and `Matrix3x2fStack#pushMatrix / translate / scale / popMatrix`, `Font#width(String)` | The status text above the hotbar. | Yes, seen in the screenshots. |

## What a screenshot settled, and what still needs a human

- **Outline drawing.** Seen working: boxes are drawn depth tested in the right colours, fill and outline sort fine.
- **Camera step timing.** The step is applied once per rendered frame from `END_EXTRACTION`, so the effect lands one frame later. The game test records the yaw tick by tick and finds no reversal and no overshoot.
- **Still needs a human on a real GPU and mouse:** how the camera *feels*, and whether the text size suits your GUI scale. The text shrinks to fit narrow windows and is laid out in GUI pixels, so it grows with the GUI scale setting.

## Porting to another Minecraft version

1. Change `minecraft_version`, `loader_version`, `fabric_api_version` and `loom_version` in `gradle.properties` to the values on the matching branch of `FabricMC/fabric-example-mod`, and the `minecraft` and `java` ranges in `fabric.mod.json`.
2. Compile. Fix errors in `GameAccess`, `HitboxRenderer`, `BoxMesh`, `DebugOverlay` and `InputHandler`. Nothing under `src/main` talks to Minecraft, so it never needs to change.
3. For pre-26 versions (1.21.x and earlier) the class names also differ (Yarn names such as `MinecraftClient`, `WorldRenderEvents`), so expect to rewrite those five files. The tests keep working untouched and describe the behaviour to preserve.
