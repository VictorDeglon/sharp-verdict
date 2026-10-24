package dev.sharp.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.sharp.client.SharpTestHooks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Runs the real game with the mod in it and plays the feature through: a named dummy to the side, an unnamed decoy dead ahead and
 * closer, and a second named dummy far to the other side. Then what the held weapon reaches (bare hands, then a spear), a
 * player-shaped mannequin, another entity standing in the way, and standing inside the target. Every check is recorded, all of
 * them run, and the test fails at the end if any failed, so one slow run on a software GPU shows everything that is wrong.
 */
public final class SharpGameTest implements FabricClientGameTest {
    /** Where the named dummy stands against the player at (0.5, 200, 0.5): 6 blocks east and 12 south. */
    private static final double EXPECTED_YAW = -Math.toDegrees(Math.atan2(6.0, 12.0));

    // The hitbox colours, as the mod's defaults define them.
    private static final int BLUE = 0xFF4D8DFF;
    private static final int YELLOW = 0xFFFFD93D;
    private static final int RED = 0xFFFF5B54;
    private static final int PURPLE = 0xFFB36BFF;

    private final List<String> report = new ArrayList<>();
    private int failures;

    private void note(String line) {
        report.add(line);
        System.out.println("SHARP-GAMETEST " + line);
    }

    private void check(boolean ok, String what) {
        note((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try {
            run(ctx);
        } finally {
            try {
                Files.writeString(Path.of("sharp-gametest-report.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("SHARP-GAMETEST could not write the report: " + e);
            }
        }
        if (failures > 0) throw new AssertionError("SHARP game test: " + failures + " check(s) failed, see sharp-gametest-report.txt");
    }

    /** Waits up to {@code maxTicks} game ticks for the condition (evaluated on the client thread). */
    private static boolean until(ClientGameTestContext ctx, int maxTicks, BooleanSupplier condition) {
        for (int i = 0; i < maxTicks; i++) {
            if (ctx.computeOnClient(mc -> condition.getAsBoolean())) return true;
            ctx.waitTick();
        }
        return ctx.computeOnClient(mc -> condition.getAsBoolean());
    }

    private static String livingNames(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            StringBuilder sb = new StringBuilder();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof LivingEntity && !(e instanceof Player)) {
                    Component n = e.getCustomName();
                    sb.append(n == null ? "<unnamed>" : "\"" + n.getString() + "\"").append(' ');
                }
            }
            return sb.toString().trim();
        });
    }

    private static float yaw(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.getYRot());
    }

    private static float pitch(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.getXRot());
    }

    /** Rounds to 3 decimals so a box read twice compares equal. */
    private static double[] round(double[] d) {
        double[] out = new double[d.length];
        for (int i = 0; i < d.length; i++) out[i] = Math.round(d[i] * 1000.0) / 1000.0;
        return out;
    }

    private void run(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(40);
            check(ctx.computeOnClient(mc -> SharpTestHooks.started()), "the mod started");

            // A stone platform in the sky: nothing in the way, so line of sight is clear and the picture is simple.
            TestServerContext server = world.getServer();
            for (String c : new String[] {
                "gamemode creative @a",
                "time set day",
                "fill -10 199 -10 30 199 30 minecraft:stone",
                "tp @a 0.5 200 0.5 0 0",
            }) {
                server.runCommand(c);
            }
            ctx.waitTicks(10);
            server.runCommand("summon minecraft:armor_stand 6.5 200 12.5 {CustomName:\"sharp dummy\",NoGravity:1b}");
            server.runCommand("summon minecraft:armor_stand 0.5 200 5.5 {NoGravity:1b}");
            server.runCommand("summon minecraft:armor_stand -14.5 200 6.5 {CustomName:\"sharp far\",NoGravity:1b}");
            ctx.waitTicks(20);
            String stands = livingNames(ctx);
            note("living non-player entities as the client sees them: " + stands);
            check(stands.contains("\"sharp dummy\""), "the named dummy exists with its name on the client");
            check(stands.contains("<unnamed>"), "the unnamed decoy exists");

            // ---- idle ----------------------------------------------------------------------------------
            ctx.getInput().lookAt(0f, 0f);
            ctx.waitTicks(5);
            check(!ctx.computeOnClient(mc -> SharpTestHooks.active()), "idle: not active before the key is held");
            check(ctx.computeOnClient(mc -> SharpTestHooks.gate()).startsWith("OPEN"), "the practice gate is open in a plain single-player world: "
                    + ctx.computeOnClient(mc -> SharpTestHooks.gate()));
            ctx.takeScreenshot("00-idle");

            // ---- hold the key: lock and outline, camera still ------------------------------------------
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "holding G activates the lock");
            ctx.waitTicks(15);
            String label = ctx.computeOnClient(mc -> SharpTestHooks.targetLabel());
            check(label.equals("sharp dummy"), "it locked the named dummy, not the closer unnamed decoy and not the other named one that is farther from the crosshair: '" + label + "'");
            check(ctx.computeOnClient(mc -> SharpTestHooks.sceneHasTarget()), "the renderer has a hitbox outline to draw");
            int parts = ctx.computeOnClient(mc -> SharpTestHooks.sceneParts());
            check(parts >= 12, "the outline is made of " + parts + " boxes (12 edges at least)");
            check(ctx.computeOnClient(mc -> SharpTestHooks.mainOutlineColor()) == BLUE, "the locked target is outlined blue: "
                    + Integer.toHexString(ctx.computeOnClient(mc -> SharpTestHooks.mainOutlineColor())));
            int[] others = ctx.computeOnClient(mc -> SharpTestHooks.otherOutlineColors());
            check(others.length == 1 && others[0] == YELLOW, "the other named dummy in range is outlined yellow, the unnamed decoy not at all: " + others.length + " outline(s)");
            check(!ctx.computeOnClient(mc -> SharpTestHooks.mainAttackable()), "13 blocks away is out of reach, so no red");
            check(ctx.computeOnClient(mc -> SharpTestHooks.exposure()).equals("OPEN"), "nothing is in the way");
            double[] aim = ctx.computeOnClient(mc -> SharpTestHooks.aimPoint());
            double[] box = ctx.computeOnClient(mc -> SharpTestHooks.mainBox());
            check(aim != null && box != null && aim[1] < box[1] + 0.4 * (box[4] - box[1]) && aim[1] > box[1], "it aims at the feet zone of the box, not the middle: y "
                    + (aim == null ? "?" : String.valueOf(aim[1])) + " in " + (box == null ? "?" : box[1] + ".." + box[4]));
            List<String> rows = ctx.computeOnClient(mc -> SharpTestHooks.cueRows());
            note("text above the hotbar:\n" + String.join("\n", rows));
            check(rows.size() == 3 && rows.get(0).startsWith("LOCKED  sharp dummy"), "the text above the hotbar names the target: " + rows);
            check(rows.size() == 3 && rows.get(2).contains("COVERED") && rows.get(2).contains("IN RANGE"), "and carries the colour key");
            check(Math.abs(yaw(ctx)) < 0.5, "camera tracking is off, so the view did not move: yaw " + yaw(ctx));
            note("angular error with the view straight ahead: " + ctx.computeOnClient(mc -> SharpTestHooks.angularError()) + " deg");
            note("panel:\n" + ctx.computeOnClient(mc -> SharpTestHooks.panelText()));
            ctx.takeScreenshot("01-locked-camera-off");

            ctx.getInput().releaseKey(InputConstants.KEY_G);
            check(until(ctx, 40, () -> !SharpTestHooks.active()), "releasing G deactivates");
            check(!ctx.computeOnClient(mc -> SharpTestHooks.sceneHasTarget()), "the outline is gone after release");
            check(ctx.computeOnClient(mc -> SharpTestHooks.lastOffReason()).equals("KEY_RELEASED"), "the reason is recorded: " + ctx.computeOnClient(mc -> SharpTestHooks.lastOffReason()));
            ctx.takeScreenshot("02-released");

            // ---- camera tracking -------------------------------------------------------------------------
            ctx.runOnClient(mc -> SharpTestHooks.setCameraTracking(true));
            ctx.getInput().lookAt(0f, 0f);
            ctx.waitTicks(3);
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "holding G activates again (a fresh press)");
            // Follow the approach tick by tick: the view must glide in one direction and never swing past the dummy.
            List<Float> trace = new ArrayList<>();
            boolean converged = false;
            for (int i = 0; i < 700 && !converged; i++) {
                trace.add(yaw(ctx));
                converged = ctx.computeOnClient(mc -> SharpTestHooks.angularError()) < 0.5;
                ctx.waitTick();
            }
            float yawNow = yaw(ctx);
            float pitchNow = pitch(ctx);
            note("approach took " + trace.size() + " ticks; yaw " + yawNow + " (expected about " + EXPECTED_YAW + "), pitch " + pitchNow
                    + ", error " + ctx.computeOnClient(mc -> SharpTestHooks.angularError()) + " deg");
            check(converged, "the camera brought the crosshair within 0.5 degrees of the dummy");
            check(Math.abs(yawNow - EXPECTED_YAW) < 1.0, "the view ended up aimed at the dummy (yaw)");
            check(pitchNow > 3.0 && pitchNow < 9.0, "and pitched down a little to the feet, not level: pitch " + pitchNow);
            check(trace.size() <= 30, "the approach took " + trace.size() + " ticks (" + trace.size() / 20.0 + " s), at most 1.5 s");

            int reversals = 0;
            float biggestStep = 0;
            float lowest = Float.MAX_VALUE;
            for (int i = 2; i < trace.size(); i++) {
                float a = trace.get(i - 1) - trace.get(i - 2);
                float b = trace.get(i) - trace.get(i - 1);
                biggestStep = Math.max(biggestStep, Math.abs(b));
                if (a * b < 0 && Math.abs(a) > 0.02f && Math.abs(b) > 0.02f) reversals++;
            }
            for (float y : trace) lowest = Math.min(lowest, y);
            note("largest single-tick turn on the way in: " + biggestStep + " deg; furthest the view went: " + lowest);
            check(reversals == 0, "the view moved in one direction the whole way in: " + reversals + " reversals");
            check(lowest >= EXPECTED_YAW - 0.3, "it never swung past the dummy: furthest " + lowest + ", dummy at " + EXPECTED_YAW);
            check(biggestStep < 0.6 * Math.abs(EXPECTED_YAW), "no sudden jump: the largest turn in one tick was " + biggestStep + " degrees of " + Math.abs(EXPECTED_YAW));

            // once settled it stays put
            float lo = Float.MAX_VALUE;
            float hi = -Float.MAX_VALUE;
            for (int i = 0; i < 40; i++) {
                float y = yaw(ctx);
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
                ctx.waitTick();
            }
            check(hi - lo < 0.3, "no oscillation once settled: yaw varied by " + (hi - lo) + " degrees over 2 seconds");
            ctx.takeScreenshot("03-tracking");

            ctx.getInput().releaseKey(InputConstants.KEY_G);
            check(until(ctx, 40, () -> !SharpTestHooks.active()), "releasing G stops the tracking");
            float atRelease = yaw(ctx);
            ctx.waitTicks(40);
            check(Math.abs(yaw(ctx) - atRelease) < 0.05, "and the camera stays where it is after release: " + atRelease + " -> " + yaw(ctx));

            // ---- nothing to lock: the player is told why --------------------------------------------------
            ctx.getInput().lookAt(0f, 0f);
            ctx.waitTicks(3);
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "active with a target in range");
            ctx.waitTicks(30);
            check(ctx.computeOnClient(mc -> SharpTestHooks.lastHint()).isEmpty(), "no message while there is something to lock: '" + ctx.computeOnClient(mc -> SharpTestHooks.lastHint()) + "'");
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            ctx.waitTicks(5);
            // Every dummy is behind the player now, far outside the 140 degree cone.
            ctx.getInput().lookAt(180f, 0f);
            ctx.waitTicks(10);
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "active with nothing in front");
            check(until(ctx, 80, () -> !SharpTestHooks.lastHint().isEmpty()), "holding the key with nothing to lock says so");
            String nothing = ctx.computeOnClient(mc -> SharpTestHooks.lastHint());
            note("hint: " + nothing);
            check(nothing.startsWith("NO TARGET") && nothing.contains("sharp") && nothing.contains("64 m"), "and says what it needs: " + nothing);
            ctx.takeScreenshot("04-no-target");
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            ctx.waitTicks(5);

            // ---- what the held weapon reaches: bare hands, then a spear --------------------------------------
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            server.runCommand("tp @a 6.5 200 8.75 0 0");
            ctx.waitTicks(15);
            ctx.getInput().lookAt(0f, 0f);
            ctx.waitTicks(5);
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "active 3.5 blocks from the dummy");
            ctx.waitTicks(10);
            check(!ctx.computeOnClient(mc -> SharpTestHooks.mainAttackable()), "bare hands do not reach 3.5 blocks");
            check(ctx.computeOnClient(mc -> SharpTestHooks.mainOutlineColor()) == BLUE, "so the target stays blue");
            check(ctx.computeOnClient(mc -> SharpTestHooks.cueRows()).get(0).startsWith("LOCKED"), "and the text says LOCKED");
            ctx.takeScreenshot("05-reach-hands");
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            ctx.waitTicks(5);

            server.runCommand("give @a minecraft:iron_spear");
            ctx.waitTicks(15);
            note("main hand: " + ctx.computeOnClient(mc -> mc.player.getMainHandItem().getItem().toString()));
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "active again, now holding a spear");
            ctx.waitTicks(10);
            check(ctx.computeOnClient(mc -> SharpTestHooks.mainAttackable()), "the spear's longer reach covers the same 3.5 blocks");
            check(ctx.computeOnClient(mc -> SharpTestHooks.mainOutlineColor()) == BLUE, "the outline stays blue, the identity of the main target");
            List<String> reachRows = ctx.computeOnClient(mc -> SharpTestHooks.cueRows());
            check(reachRows.get(0).startsWith("IN REACH  sharp dummy"), "and the text says IN REACH: " + reachRows.get(0));
            ctx.takeScreenshot("06-reach-spear");

            // ---- a player-shaped entity, with its real hitbox -------------------------------------------------
            server.runCommand("summon minecraft:mannequin 3.5 200 10.5 {CustomName:\"sharp mannequin\"}");
            ctx.waitTicks(20);
            note("living entities now: " + livingNames(ctx));
            double[] mannequinBox = ctx.computeOnClient(mc -> {
                for (Entity e : mc.level.entitiesForRendering()) {
                    Component n = e.getCustomName();
                    if (n != null && n.getString().equals("sharp mannequin")) {
                        var b = e.getBoundingBox();
                        return new double[] {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ};
                    }
                }
                return null;
            });
            check(mannequinBox != null, "the mannequin exists on the client");
            if (mannequinBox != null) {
                check(Math.abs((mannequinBox[3] - mannequinBox[0]) - 0.6) < 0.01 && Math.abs((mannequinBox[4] - mannequinBox[1]) - 1.8) < 0.01,
                        "it has the player's hitbox, 0.6 wide and 1.8 tall");
                List<double[]> drawn = ctx.computeOnClient(mc -> SharpTestHooks.otherBoxes());
                boolean found = drawn.stream().anyMatch(d -> java.util.Arrays.equals(round(d), round(mannequinBox)));
                check(found, "and the mod outlines exactly that box (" + drawn.size() + " other outline(s))");
                boolean reachable = ctx.computeOnClient(mc -> mc.player.isWithinAttackRange(mc.player.getMainHandItem(),
                        new net.minecraft.world.phys.AABB(mannequinBox[0], mannequinBox[1], mannequinBox[2], mannequinBox[3], mannequinBox[4], mannequinBox[5]), 0.0));
                int[] colours = ctx.computeOnClient(mc -> SharpTestHooks.otherOutlineColors());
                boolean hasExpected = java.util.Arrays.stream(colours).anyMatch(c -> c == (reachable ? RED : YELLOW));
                check(hasExpected, "its outline is " + (reachable ? "red, the spear reaches it" : "yellow, the spear does not reach it"));
            }
            ctx.takeScreenshot("07-mannequin");
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            ctx.waitTicks(5);
            server.runCommand("kill @e[type=minecraft:mannequin]");

            // ---- another entity stands in the way ---------------------------------------------------------------
            server.runCommand("summon minecraft:armor_stand 6.5 200 10.0 {CustomName:\"blocker\",NoGravity:1b}");
            ctx.waitTicks(15);
            ctx.getInput().lookAt(0f, 0f);
            ctx.waitTicks(3);
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "active with a blocker between the player and the dummy");
            ctx.waitTicks(10);
            String covered = ctx.computeOnClient(mc -> SharpTestHooks.targetLabel());
            check(covered.equals("sharp dummy"), "the lock is still on the dummy, the blocker is not a practice entity: " + covered);
            check(ctx.computeOnClient(mc -> SharpTestHooks.exposure()).equals("COVERED"), "and the mod knows it is covered: " + ctx.computeOnClient(mc -> SharpTestHooks.exposure()));
            check(ctx.computeOnClient(mc -> SharpTestHooks.mainOutlineColor()) == PURPLE, "the target is outlined purple");
            check(ctx.computeOnClient(mc -> SharpTestHooks.cueRows()).get(0).startsWith("COVERED"), "and the text says COVERED: " + ctx.computeOnClient(mc -> SharpTestHooks.cueRows()).get(0));
            ctx.takeScreenshot("08-covered");
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            ctx.waitTicks(5);
            server.runCommand("kill @e[type=minecraft:armor_stand,name=blocker]");
            ctx.waitTicks(10);

            // ---- the player stands inside the target ---------------------------------------------------------------
            ctx.runOnClient(mc -> SharpTestHooks.setCameraTracking(true));
            server.runCommand("tp @a 6.5 200 12.5 0 0");
            ctx.waitTicks(15);
            ctx.getInput().lookAt(0f, 0f);
            ctx.waitTicks(3);
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "active while standing inside the dummy");
            ctx.waitTicks(10);
            check(ctx.computeOnClient(mc -> SharpTestHooks.exposure()).equals("INSIDE"), "the mod knows the eye is inside the hitbox: " + ctx.computeOnClient(mc -> SharpTestHooks.exposure()));
            check(ctx.computeOnClient(mc -> SharpTestHooks.mainOutlineColor()) == PURPLE, "the target is outlined purple");
            float insideYaw = yaw(ctx);
            ctx.waitTicks(20);
            check(Math.abs(yaw(ctx) - insideYaw) < 0.05, "the camera holds still instead of whipping around a point inside it");
            ctx.takeScreenshot("09-inside");
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            ctx.waitTicks(5);
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0.5 200 0.5 0 0");
            ctx.waitTicks(10);

            // ---- the status text can be made quiet or switched off ------------------------------------------
            ctx.getInput().lookAt(0f, 0f);
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> SharpTestHooks.setStatusStyle("auto"));
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "active for the text style checks");
            ctx.waitTicks(5);
            check(ctx.computeOnClient(mc -> SharpTestHooks.cueRows()).size() == 3 && !ctx.computeOnClient(mc -> SharpTestHooks.cuesMinimal()),
                    "auto starts with the full text: " + ctx.computeOnClient(mc -> SharpTestHooks.cueRows()).size() + " rows");
            check(until(ctx, 400, SharpTestHooks::cuesMinimal), "and after a few seconds of the same hold it quietens down by itself");
            List<String> quiet = ctx.computeOnClient(mc -> SharpTestHooks.cueRows());
            check(quiet.size() == 1 && quiet.get(0).startsWith("LOCKED"), "to a single line: " + quiet);
            ctx.takeScreenshot("10-auto-quiet");
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            ctx.waitTicks(5);

            ctx.runOnClient(mc -> SharpTestHooks.setStatusStyle("minimal"));
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "active with the minimal style");
            ctx.waitTicks(5);
            List<String> minimalRows = ctx.computeOnClient(mc -> SharpTestHooks.cueRows());
            check(minimalRows.size() == 1 && ctx.computeOnClient(mc -> SharpTestHooks.cuesMinimal()), "minimal is one line from the very start: " + minimalRows);

            ctx.runOnClient(mc -> SharpTestHooks.setStatusStyle("off"));
            ctx.waitTicks(3);
            check(ctx.computeOnClient(mc -> SharpTestHooks.cueRows()).isEmpty(), "off draws no text at all");
            check(ctx.computeOnClient(mc -> SharpTestHooks.sceneHasTarget()) && ctx.computeOnClient(mc -> SharpTestHooks.mainOutlineColor()) == BLUE,
                    "while the blue outline is still there");
            ctx.takeScreenshot("11-text-off");
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> SharpTestHooks.setStatusStyle("auto"));

            // ---- a screen opening ------------------------------------------------------------------------
            ctx.runOnClient(mc -> SharpTestHooks.setCameraTracking(false));
            ctx.getInput().lookAt(0f, 0f);
            ctx.waitTicks(3);
            ctx.getInput().holdKey(InputConstants.KEY_G);
            check(until(ctx, 80, SharpTestHooks::active), "active again before the pause screen");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);
            check(!ctx.computeOnClient(mc -> SharpTestHooks.active()), "opening the pause screen switched it off");
            note("reason: " + ctx.computeOnClient(mc -> SharpTestHooks.lastOffReason()));
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);
            ctx.getInput().releaseKey(InputConstants.KEY_G);
            ctx.waitTicks(5);

            // ---- a world opened to LAN -------------------------------------------------------------------
            server.runCommand("publish");
            ctx.waitTicks(30);
            boolean published = ctx.computeOnClient(mc -> mc.getSingleplayerServer() != null && mc.getSingleplayerServer().isPublished());
            note("the world is open to LAN: " + published);
            if (published) {
                ctx.getInput().holdKey(InputConstants.KEY_G);
                ctx.waitTicks(20);
                check(!ctx.computeOnClient(mc -> SharpTestHooks.active()), "an open-to-LAN world never activates");
                check(ctx.computeOnClient(mc -> SharpTestHooks.gate()).contains("LAN"), "and the panel says why: " + ctx.computeOnClient(mc -> SharpTestHooks.gate()));
                check(ctx.computeOnClient(mc -> SharpTestHooks.lastHint()).contains("LAN"), "and so does the on-screen message: '" + ctx.computeOnClient(mc -> SharpTestHooks.lastHint()) + "'");
                ctx.getInput().releaseKey(InputConstants.KEY_G);
            } else {
                note("SKIP the LAN check: the world could not be opened to LAN in this environment");
            }

            check(!ctx.computeOnClient(mc -> SharpTestHooks.faulted()), "the mod never hit an internal error");
        }
    }
}
