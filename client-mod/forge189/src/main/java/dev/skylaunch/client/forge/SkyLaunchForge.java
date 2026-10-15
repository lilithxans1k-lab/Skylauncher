package dev.skylaunch.client.forge;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.features.Extras;
import dev.skylaunch.client.features.Visual;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Mod(modid = "skylaunch", useMetadata = true)
public final class SkyLaunchForge {
    private static Client client;
    private final Set<Integer> held = new HashSet<Integer>();
    private boolean sprintHeldByUs;
    private boolean fullbrightOn;
    private float savedGamma;
    private boolean viewBobOff;
    private boolean savedViewBob;

    public static Client client() {
        return client;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        dev.skylaunch.client.module.Appearance.canBlur = false; // 1.8.9 has no menu blur
        client = new Client(new ForgeGame());
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        client.tick();
        pollKeys(mc);
        applyToggleSprint(mc);
        applyToggleSneak(mc);
        applyFullbright(mc);
        applyViewBob(mc);
    }

    /** No view bobbing: switch the vanilla option off while on, put yours back when off. */
    private void applyViewBob(Minecraft mc) {
        boolean want = client.noViewBob.enabled;
        if (want && !viewBobOff) {
            savedViewBob = mc.gameSettings.viewBobbing;
            viewBobOff = true;
        }
        if (want) {
            mc.gameSettings.viewBobbing = false;
        } else if (viewBobOff) {
            mc.gameSettings.viewBobbing = savedViewBob;
            viewBobOff = false;
        }
    }

    private void pollKeys(Minecraft mc) {
        if (mc.currentScreen != null || mc.thePlayer == null) {
            held.clear();
            return;
        }
        int[] keys = client.watchedKeys();
        for (int code : keys) {
            if (code <= 0) continue;
            boolean down = client.game.keyDown(code);
            if (down && held.add(code)) client.onKey(code);
            else if (!down) held.remove(code);
        }
    }

    private boolean sneakHeldByUs;

    private void applyToggleSneak(Minecraft mc) {
        int sneakKey = mc.gameSettings.keyBindSneak.getKeyCode();
        boolean want = client.toggleSprint.enabled && client.toggleSprint.sneakToggled && mc.thePlayer != null && mc.currentScreen == null;
        if (want) {
            KeyBinding.setKeyBindState(sneakKey, true);
            sneakHeldByUs = true;
        } else if (sneakHeldByUs) {
            KeyBinding.setKeyBindState(sneakKey, false);
            sneakHeldByUs = false;
        }
    }

    private void applyToggleSprint(Minecraft mc) {
        int sprintKey = mc.gameSettings.keyBindSprint.getKeyCode();
        boolean want = client.toggleSprint.enabled && client.toggleSprint.toggled && mc.thePlayer != null && mc.currentScreen == null;
        if (want) {
            KeyBinding.setKeyBindState(sprintKey, true);
            sprintHeldByUs = true;
        } else if (sprintHeldByUs) {
            KeyBinding.setKeyBindState(sprintKey, false);
            sprintHeldByUs = false;
        }
    }

    private void applyFullbright(Minecraft mc) {
        boolean want = client.fullbright.enabled;
        if (want && !fullbrightOn) {
            savedGamma = mc.gameSettings.gammaSetting;
            fullbrightOn = true;
        }
        if (want) {
            mc.gameSettings.gammaSetting = 100f;
        } else if (fullbrightOn) {
            mc.gameSettings.gammaSetting = savedGamma;
            fullbrightOn = false;
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.type == 2) {
            client.onActionBar(event.message.getUnformattedText());
            return;
        }
        if (event.message != null) client.onChat(event.message.getUnformattedText());
        if (client.chatTimestamps.enabled && event.message != null) {
            IChatComponent stamped = new ChatComponentText("\u00a7" + client.chatTimestamps.code() + client.chatTimestamps.stamp(new Date()) + "\u00a7r");
            stamped.appendSibling(event.message);
            event.message = stamped;
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.buttonstate && (event.button == 0 || event.button == 1)) client.onMouseClick(event.button);
        Minecraft mc = Minecraft.getMinecraft();
        if (event.buttonstate && event.button == 0 && mc.objectMouseOver != null && mc.thePlayer != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mc.objectMouseOver.hitVec != null) {
            client.onAttackEntity(mc.thePlayer.getPositionEyes(1f).distanceTo(mc.objectMouseOver.hitVec));
        }
        if (event.dwheel != 0 && client.onScroll(event.dwheel > 0 ? 1 : -1)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
        Extras.HideVanillaHud hide = client.hideVanillaHud;
        switch (event.type) {
            case HEALTH: if (hide.hide(hide.hearts)) { event.setCanceled(true); return; } break;
            case ARMOR: if (hide.hide(hide.armor)) { event.setCanceled(true); return; } break;
            case FOOD: if (hide.hide(hide.food)) { event.setCanceled(true); return; } break;
            case EXPERIENCE: if (hide.hide(hide.xp)) { event.setCanceled(true); return; } break;
            case BOSSHEALTH: if (hide.hide(hide.bossBar)) { event.setCanceled(true); return; } break;
            default: break;
        }
        if (event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && client.crosshair.enabled) {
            event.setCanceled(true);
            ForgeGfx g = new ForgeGfx();
            GlStateManager.enableBlend();
            client.crosshair.draw(g, g.width() / 2, g.height() / 2);
        }
    }

    @SubscribeEvent
    public void onOverlayPost(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (Minecraft.getMinecraft().gameSettings.showDebugInfo) return;
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        client.renderHud(new ForgeGfx());
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onBlockHighlight(DrawBlockHighlightEvent event) {
        Visual.BlockOutline bo = client.blockOutline;
        if (!bo.enabled || event.target == null || event.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        Minecraft mc = Minecraft.getMinecraft();
        BlockPos pos = event.target.getBlockPos();
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block.getMaterial() == net.minecraft.block.material.Material.air || !mc.theWorld.getWorldBorder().contains(pos)) return;
        event.setCanceled(true);

        EntityPlayer p = event.player;
        float pt = event.partialTicks;
        double rx = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        double ry = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
        double rz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, pos).expand(0.002, 0.002, 0.002).offset(-rx, -ry, -rz);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        fill(box, bo.fill.argb);
        int line = bo.line.argb;
        GlStateManager.color(((line >> 16) & 255) / 255f, ((line >> 8) & 255) / 255f, (line & 255) / 255f, (line >>> 24) / 255f);
        GL11.glLineWidth((float) bo.width.value);
        RenderGlobal.drawSelectionBoundingBox(box);
        GL11.glLineWidth(1f);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void fill(AxisAlignedBB b, int argb) {
        if ((argb >>> 24) == 0) return;
        GlStateManager.color(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer w = t.getWorldRenderer();
        w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
        // bottom, top, north, south, west, east
        quad(w, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(w, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(w, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(w, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(w, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(w, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        t.draw();
    }

    private static void quad(WorldRenderer w, double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz) {
        w.pos(ax, ay, az).endVertex();
        w.pos(bx, by, bz).endVertex();
        w.pos(cx, cy, cz).endVertex();
        w.pos(dx, dy, dz).endVertex();
    }
}
