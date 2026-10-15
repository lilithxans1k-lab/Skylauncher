package dev.skylaunch.client.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;

/** Filled box geometry (same approach as Fabric API's own render test). */
final class Boxes {
    private Boxes() {}

    static void filled(PoseStack.Pose pose, VertexConsumer v, AABB b, int c) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        quad(pose, v, c, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0); // north
        quad(pose, v, c, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1); // south
        quad(pose, v, c, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1); // west
        quad(pose, v, c, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0); // east
        quad(pose, v, c, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1); // top
        quad(pose, v, c, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0); // bottom
    }

    private static void quad(PoseStack.Pose p, VertexConsumer v, int c,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        v.addVertex(p, ax, ay, az).setColor(c);
        v.addVertex(p, bx, by, bz).setColor(c);
        v.addVertex(p, cx, cy, cz).setColor(c);
        v.addVertex(p, dx, dy, dz).setColor(c);
    }
}
