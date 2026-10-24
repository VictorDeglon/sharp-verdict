package dev.sharp.client;

import dev.sharp.config.SharpConfig;
import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import dev.sharp.core.targeting.LineOfSight;
import dev.sharp.core.targeting.TargetCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The only class that reads game state in ways the rest of the mod does not. It exists so that a version
 * change in Minecraft only ever needs edits in one place.
 *
 * <p>API status, checked against Fabric API 26.3 sources and the Fabric docs reference mods:
 *
 * <ul>
 *   <li>{@code VERIFIED}: the call appears in code that compiles upstream against 26.3.
 *   <li>{@code UNVERIFIED}: no upstream source in reach uses it. The name and signature are the
 *       Mojang-mapped ones used since 1.20, which 26.x kept, but nothing has compiled them. If a build
 *       complains, look here first. See docs/API_NOTES.md.
 * </ul>
 */
final class GameAccess {
    /** Vanilla's mouse-to-degrees factor in {@code Entity#turn}: 0.15 degrees per cursor unit. */
    static final double DEGREES_PER_CURSOR_UNIT = 0.15;

    private GameAccess() {
    }

    // ---- the single-player practice gate -----------------------------------------------------------

    /**
     * Why the practice gate is closed, or null when it is open. The feature only runs in a world hosted by
     * this very client (the integrated server) and never once that world is opened to other players.
     */
    static String practiceGateClosedReason(Minecraft mc) {
        var server = mc.getSingleplayerServer(); // VERIFIED (RecipeSyncClientTest)
        if (server == null) {
            return "not a single-player world";
        }
        if (server.isPublished()) { // UNVERIFIED: IntegratedServer#isPublished()
            return "world is open to LAN";
        }
        return null;
    }

    // ---- player state ------------------------------------------------------------------------------

    static boolean windowActive(Minecraft mc) {
        return mc.isWindowActive(); // UNVERIFIED: Minecraft#isWindowActive()
    }

    static boolean playerAlive(LocalPlayer player) {
        return player.isAlive() && !player.isDeadOrDying(); // isDeadOrDying VERIFIED; isAlive UNVERIFIED but ancient
    }

    static V3 eyePosition(Entity entity) {
        Vec3 eye = entity.getEyePosition(); // VERIFIED (DebugApiTestClient)
        return new V3(eye.x, eye.y, eye.z); // VERIFIED (LevelRenderEventsTests uses .x/.y/.z)
    }

    // ---- practice entities -------------------------------------------------------------------------

    /**
     * Every living entity that carries the practice marker and is close enough to matter: mobs, armor stands, mannequins and
     * player-like bodies (fake-player bots) alike, all with their real bounding boxes. The player is never one. Entities without
     * the marker are never returned, so nothing else can ever be targeted.
     */
    static List<TargetCandidate> candidates(ClientLevel level, LocalPlayer player, SharpConfig config) {
        double reach = config.range * (1.0 + config.retainMarginPercent / 100.0) + 2.0;
        V3 eye = eyePosition(player);
        List<TargetCandidate> out = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) { // VERIFIED (ClientEntityLifecycleTests)
            if (entity == player || !(entity instanceof LivingEntity living)) {
                continue;
            }
            if (entity.isRemoved() || !living.isAlive()) { // isRemoved VERIFIED
                continue;
            }
            if (!isPracticeEntity(entity, config.practiceMarker)) {
                continue;
            }
            Box box = boundingBox(entity);
            if (box.distanceTo(eye) > reach) {
                continue;
            }
            // Head yaw and the raised-shield state decide where the back of the target is; both are plain reads on LivingEntity.
            out.add(new TargetCandidate(entity.getId(), label(entity), box, living.getYHeadRot(), living.isBlocking())); // getId VERIFIED
        }
        return out;
    }

    /**
     * Every entity that an attack could land on instead of the target: anything the game lets the crosshair pick (mobs, players,
     * armor stands, boats), practice marker or not. Only boxes are needed, so nothing about them is stored.
     */
    static List<TargetCandidate> blockers(ClientLevel level, LocalPlayer player, SharpConfig config) {
        double reach = config.range * (1.0 + config.retainMarginPercent / 100.0) + 2.0;
        V3 eye = eyePosition(player);
        List<TargetCandidate> out = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || entity.isRemoved() || entity.isSpectator() || !entity.isPickable()) { // isPickable VERIFIED to exist
                continue;
            }
            Box box = boundingBox(entity);
            if (box.distanceTo(eye) <= reach) {
                out.add(new TargetCandidate(entity.getId(), "", box));
            }
        }
        return out;
    }

    /**
     * True when the entity's custom name starts with the marker or its scoreboard team is named exactly that. A player-like body
     * (fake player, mannequin) is also one when its displayed name starts with the marker, since those carry their name as a profile
     * name and not as a custom name.
     */
    static boolean isPracticeEntity(Entity entity, String marker) {
        String m = marker.toLowerCase(Locale.ROOT);
        Component custom = entity.getCustomName(); // UNVERIFIED: Entity#getCustomName()
        if (custom != null && custom.getString().toLowerCase(Locale.ROOT).startsWith(m)) {
            return true;
        }
        if (entity instanceof Avatar && entity.getName().getString().toLowerCase(Locale.ROOT).startsWith(m)) {
            return true;
        }
        var team = entity.getTeam(); // VERIFIED to exist (FakePlayer overrides it)
        return team != null && team.getName().equalsIgnoreCase(marker); // UNVERIFIED: Team#getName()
    }

    /**
     * Whether the weapon in the main hand could hit an entity with this box from where the player stands. Asks the game itself, so
     * the answer follows the item: a sword, a mace, a spear with its longer reach, bare hands.
     */
    static Predicate<Box> attackRange(LocalPlayer player) {
        var stack = player.getMainHandItem();
        return box -> player.isWithinAttackRange(stack, new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()), 0.0);
    }

    private static String label(Entity entity) {
        return entity.getName().getString(); // VERIFIED (EntityEventTests)
    }

    private static Box boundingBox(Entity entity) {
        AABB b = entity.getBoundingBox(); // UNVERIFIED: Entity#getBoundingBox()
        return new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); // VERIFIED field names (TestRenderUtils)
    }

    // ---- line of sight -----------------------------------------------------------------------------

    /** Ray test against blocks only. Entities and fluids do not block the view of a target. */
    static LineOfSight lineOfSight(ClientLevel level, Entity viewer) {
        return (from, to) -> {
            // UNVERIFIED: ClipContext(Vec3, Vec3, Block, Fluid, Entity) and Level#clip(ClipContext)
            ClipContext context = new ClipContext(
                    new Vec3(from.x(), from.y(), from.z()),
                    new Vec3(to.x(), to.y(), to.z()),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    viewer);
            return level.clip(context).getType() == HitResult.Type.MISS; // HitResult.Type.MISS VERIFIED
        };
    }

    // ---- camera ------------------------------------------------------------------------------------

    /**
     * Rotates the player's view by the given degrees (positive yaw turns clockwise seen from above,
     * positive pitch looks down). Goes through the same method the mouse uses, which also keeps the
     * previous-tick rotation in step, so the view stays smooth between ticks.
     */
    static void turn(LocalPlayer player, double deltaYawDegrees, double deltaPitchDegrees) {
        // UNVERIFIED: Entity#turn(double yRot, double xRot) takes cursor units, 0.15 degrees each
        player.turn(deltaYawDegrees / DEGREES_PER_CURSOR_UNIT, deltaPitchDegrees / DEGREES_PER_CURSOR_UNIT);
    }
}
