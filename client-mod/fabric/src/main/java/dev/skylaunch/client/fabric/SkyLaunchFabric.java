package dev.skylaunch.client.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skylaunch.client.Client;
import dev.skylaunch.client.features.Visual;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Fabric entrypoint: wires the shared client into Minecraft 26.2. */
public final class SkyLaunchFabric implements ClientModInitializer {
    private static Client client;
    private final Set<Integer> held = new HashSet<>();
    private boolean sprintHeldByUs;

    public static Client client() {
        return client;
    }

    @Override
    public void onInitializeClient() {
        client = new Client(new FabricGame());
        Visual.BlockOutline.canStyleLines = false;

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            client.tick();
            pollKeys(mc);
            applyToggleSprint(mc);
            HitColourHook.update(client);
        });

        // Action bar (health, mana, skill XP).
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) client.onActionBar(message.getString());
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skylaunch", "hud"),
                (graphics, delta) -> client.renderHud(new FabricGfx(graphics)));

        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (client.crosshair.enabled && mc.options.getCameraType().isFirstPerson()) {
                client.crosshair.draw(new FabricGfx(graphics), graphics.guiWidth() / 2, graphics.guiHeight() / 2);
            } else {
                vanilla.extractRenderState(graphics, delta);
            }
        });

        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            Visual.BlockOutline bo = client.blockOutline;
            if (!bo.enabled) return true;
            PoseStack pose = context.poseStack();
            Vec3 cam = context.levelState().cameraRenderState.pos;
            BlockPos pos = outline.pos();
            pose.pushPose();
            pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            AABB box = new AABB(0, 0, 0, 1, 1, 1).inflate(0.002);
            int colour = bo.fill.argb;
            context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugFilledBox(),
                    (p, buffer) -> Boxes.filled(p, buffer, box, colour));
            pose.popPose();
            return bo.keepVanilla.value;
        });
    }

    /** Detects presses of SkyLaunch keys (menu, toggle sprint) while playing. */
    private void pollKeys(Minecraft mc) {
        if (mc.gui.screen() != null || mc.player == null) {
            held.clear();
            return;
        }
        int[] keys = {client.appearance.menuKey.code, client.toggleSprint.key.code};
        for (int code : keys) {
            if (code < 0) continue;
            boolean down = client.game.keyDown(code);
            if (down && held.add(code)) client.onKey(code);
            else if (!down) held.remove(code);
        }
    }

    private void applyToggleSprint(Minecraft mc) {
        boolean want = client.toggleSprint.enabled && client.toggleSprint.toggled && mc.player != null && mc.gui.screen() == null;
        if (want) {
            mc.options.keySprint.setDown(true);
            sprintHeldByUs = true;
        } else if (sprintHeldByUs) {
            mc.options.keySprint.setDown(false);
            sprintHeldByUs = false;
        }
    }
}
