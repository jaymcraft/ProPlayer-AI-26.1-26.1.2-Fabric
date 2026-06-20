package net.shasankp000.GraphicalUserInterface;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.Overlay.ThreatDebugManager;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;

/**
 * Renders threat analysis debug information as colored bounding boxes
 * Similar to Minecraft's F3+B hitbox display
 */
public class ThreatDebugRenderer {

    private static final int TARGET_BOX_COLOR = 0x80FF0000; // Red with transparency
    private static final int EVALUATED_BOX_COLOR = 0x80FFAA00; // Orange with transparency

    /**
     * Render debug overlays for all tracked entities
     */
    public static void renderThreatOverlays(Camera camera) {
        if (!ThreatDebugManager.isDebugEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null)
            return;

        Map<UUID, ThreatDebugManager.ThreatInfo> threats = ThreatDebugManager.getAllThreats();
        UUID currentTarget = ThreatDebugManager.getCurrentTarget();

        Vec3 cameraPos = camera.position();

        for (Map.Entry<UUID, ThreatDebugManager.ThreatInfo> entry : threats.entrySet()) {
            // Find entity by UUID in the world
            Entity entity = null;
            for (Entity e : client.level.entitiesForRendering()) {
                if (e.getUUID().equals(entry.getKey())) {
                    entity = e;
                    break;
                }
            }

            if (entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
                boolean isTarget = entry.getKey().equals(currentTarget);

                // Render bounding box only
                // Red for target, orange for evaluated threats
                int boxColor = isTarget ? TARGET_BOX_COLOR : EVALUATED_BOX_COLOR;
                renderEntityBoundingBox(livingEntity, boxColor, cameraPos);
            }
        }
    }

    /**
     * Render a simple bounding box for an entity
     */
    private static void renderEntityBoundingBox(LivingEntity entity, int boxColor, Vec3 cameraPos) {
        // Calculate distance to camera
        double distance = entity.position().distanceTo(cameraPos);
        if (distance > 64.0)
            return; // Don't render if too far

        AABB box = entity.getBoundingBox();

        // Setup matrices
        PoseStack matrices = new PoseStack();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Temporarily disabled due to massive 26.1 RenderSystem changes.
        // Needs migration to RenderPipelines.

    }

    /**
     * Draw the 12 edges of a bounding box (same as Minecraft hitbox display)
     */
    private static void drawBoxEdges(Matrix4f matrix, BufferBuilder buffer, AABB box, float r, float g, float b,
            float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Bottom face edges
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);

        // Top face edges
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);

        // Vertical edges (connecting bottom to top)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);

        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
    }

}
