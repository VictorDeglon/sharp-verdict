package dev.sharp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sharp.core.math.Box;

/**
 * Writes one solid box into a vertex buffer as six quads. Same vertex order as the box drawer in Fabric
 * API's own rendering test mod, which is the pattern known to render with {@code RenderTypes.debugFilledBox()}.
 */
final class BoxMesh {
    private BoxMesh() {
    }

    static void filled(PoseStack.Pose pose, VertexConsumer buffer, Box box, int argb) {
        float x0 = (float) box.minX();
        float y0 = (float) box.minY();
        float z0 = (float) box.minZ();
        float x1 = (float) box.maxX();
        float y1 = (float) box.maxY();
        float z1 = (float) box.maxZ();

        // Front (min Z)
        vertex(buffer, pose, x0, y0, z0, argb);
        vertex(buffer, pose, x1, y0, z0, argb);
        vertex(buffer, pose, x1, y1, z0, argb);
        vertex(buffer, pose, x0, y1, z0, argb);
        // Back (max Z)
        vertex(buffer, pose, x1, y0, z1, argb);
        vertex(buffer, pose, x0, y0, z1, argb);
        vertex(buffer, pose, x0, y1, z1, argb);
        vertex(buffer, pose, x1, y1, z1, argb);
        // Left (min X)
        vertex(buffer, pose, x0, y0, z1, argb);
        vertex(buffer, pose, x0, y0, z0, argb);
        vertex(buffer, pose, x0, y1, z0, argb);
        vertex(buffer, pose, x0, y1, z1, argb);
        // Right (max X)
        vertex(buffer, pose, x1, y0, z0, argb);
        vertex(buffer, pose, x1, y0, z1, argb);
        vertex(buffer, pose, x1, y1, z1, argb);
        vertex(buffer, pose, x1, y1, z0, argb);
        // Top (max Y)
        vertex(buffer, pose, x0, y1, z0, argb);
        vertex(buffer, pose, x1, y1, z0, argb);
        vertex(buffer, pose, x1, y1, z1, argb);
        vertex(buffer, pose, x0, y1, z1, argb);
        // Bottom (min Y)
        vertex(buffer, pose, x0, y0, z1, argb);
        vertex(buffer, pose, x1, y0, z1, argb);
        vertex(buffer, pose, x1, y0, z0, argb);
        vertex(buffer, pose, x0, y0, z0, argb);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int argb) {
        buffer.addVertex(pose, x, y, z).setColor(argb);
    }
}
