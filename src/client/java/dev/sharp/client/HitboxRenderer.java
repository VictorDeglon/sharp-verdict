package dev.sharp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sharp.core.math.V3;
import dev.sharp.core.render.ColoredBox;
import dev.sharp.core.render.HitboxMeshBuilder;
import dev.sharp.core.render.HitboxScene;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the target's real bounding box. It only ever draws what the controller put in the scene, which is
 * the locked target (and, if enabled, the other practice entities in range), so unrelated entities are never
 * touched. What to draw, colours, widths and distance culling are decided in the core; this class just
 * submits the resulting boxes.
 *
 * <p>Drawing follows the pattern in Fabric API's own LevelRenderEventsTests: translate the pose stack
 * by the camera position and submit custom geometry with {@code RenderTypes.debugFilledBox()}.
 */
final class HitboxRenderer {
    private HitboxRenderer() {
    }

    static void register(SharpRuntime runtime, ConfigManager configs) {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> render(context, runtime, configs));
    }

    private static void render(LevelRenderContext context, SharpRuntime runtime, ConfigManager configs) {
        HitboxScene scene = runtime.scene();
        if (scene.isEmpty()) {
            return;
        }
        Vec3 cameraPos = context.levelState().cameraRenderState.pos; // VERIFIED (LevelRenderEventsTests)
        V3 camera = new V3(cameraPos.x, cameraPos.y, cameraPos.z);

        List<ColoredBox> parts = HitboxMeshBuilder.build(scene, configs.get().hitbox(), camera);
        if (parts.isEmpty()) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
            for (ColoredBox part : parts) {
                BoxMesh.filled(pose, buffer, part.box(), part.argb());
            }
        });
        poseStack.popPose();
    }
}
