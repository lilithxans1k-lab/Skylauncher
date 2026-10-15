package dev.skylaunch.client.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skylaunch.client.Game;
import dev.skylaunch.client.ui.UiScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Game data for Minecraft 26.2 (Mojang names). */
final class FabricGame implements Game {
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public boolean inWorld() {
        return mc.player != null && mc.level != null;
    }

    @Override
    public int fps() {
        return mc.getFps();
    }

    @Override
    public int ping() {
        ClientPacketListener net = mc.getConnection();
        if (net == null || mc.player == null) return -1;
        PlayerInfo info = net.getPlayerInfo(mc.player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    @Override
    public double x() {
        return mc.player == null ? 0 : mc.player.getX();
    }

    @Override
    public double y() {
        return mc.player == null ? 0 : mc.player.getY();
    }

    @Override
    public double z() {
        return mc.player == null ? 0 : mc.player.getZ();
    }

    @Override
    public float yaw() {
        return mc.player == null ? 0 : mc.player.getYRot();
    }

    @Override
    public boolean sprinting() {
        return mc.player != null && mc.player.isSprinting();
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Override
    public List<ItemInfo> armorAndHeld() {
        LocalPlayer p = mc.player;
        if (p == null) return Collections.emptyList();
        List<ItemInfo> out = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR) add(out, p.getItemBySlot(slot), false);
        add(out, p.getMainHandItem(), true);
        return out;
    }

    private static void add(List<ItemInfo> out, ItemStack s, boolean held) {
        if (s == null || s.isEmpty()) return;
        boolean dmg = s.isDamageableItem();
        out.add(new ItemInfo(s, dmg ? s.getDamageValue() : 0, dmg ? s.getMaxDamage() : 0, s.getCount(), held));
    }

    @Override
    public List<EffectInfo> effects() {
        LocalPlayer p = mc.player;
        if (p == null) return Collections.emptyList();
        List<EffectInfo> out = new ArrayList<>();
        for (MobEffectInstance e : p.getActiveEffects()) {
            String name = e.getEffect().value().getDisplayName().getString();
            out.add(new EffectInfo(name, e.getAmplifier(), e.isInfiniteDuration() ? -1 : e.getDuration()));
        }
        return out;
    }

    @Override
    public String scoreboardTitle() {
        if (mc.level == null) return "";
        Objective o = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return o == null ? "" : o.getDisplayName().getString();
    }

    @Override
    public List<String> scoreboardLines() {
        if (mc.level == null) return Collections.emptyList();
        Scoreboard sb = mc.level.getScoreboard();
        Objective objective = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return Collections.emptyList();
        List<String> lines = new ArrayList<>();
        for (ScoreHolder holder : sb.getTrackedPlayers()) {
            if (!sb.listPlayerScores(holder).containsKey(objective)) continue;
            PlayerTeam team = sb.getPlayersTeam(holder.getScoreboardName());
            if (team == null) continue;
            String line = team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
            if (!line.trim().isEmpty()) lines.add(line);
        }
        return lines;
    }

    @Override
    public List<String> tabLines() {
        ClientPacketListener net = mc.getConnection();
        if (net == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (PlayerInfo info : net.getOnlinePlayers()) {
            Component c = info.getTabListDisplayName();
            if (c != null) out.add(c.getString());
        }
        return out;
    }

    @Override
    public boolean keyDown(int keyCode) {
        if (keyCode < 0) return false;
        if (keyCode <= 7) return false; // mouse buttons aren't supported as SkyLaunch keys
        return InputConstants.isKeyDown(mc.getWindow(), keyCode);
    }

    @Override
    public String keyName(int keyCode) {
        if (keyCode < 0) return "None";
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
    }

    @Override
    public int key(String name) {
        return Keys.glfw(name);
    }

    @Override
    public boolean forwardDown() {
        return mc.options.keyUp.isDown();
    }

    @Override
    public boolean backDown() {
        return mc.options.keyDown.isDown();
    }

    @Override
    public boolean leftDown() {
        return mc.options.keyLeft.isDown();
    }

    @Override
    public boolean rightDown() {
        return mc.options.keyRight.isDown();
    }

    @Override
    public boolean jumpDown() {
        return mc.options.keyJump.isDown();
    }

    @Override
    public boolean attackDown() {
        return mc.options.keyAttack.isDown();
    }

    @Override
    public boolean useDown() {
        return mc.options.keyUse.isDown();
    }

    @Override
    public void openScreen(UiScreen screen) {
        mc.gui.setScreen(new ScreenWrapper(screen));
    }

    @Override
    public void closeScreen() {
        mc.gui.setScreen(null);
    }

    @Override
    public File configDir() {
        return FabricLoader.getInstance().getConfigDir().toFile();
    }

    @Override
    public String platformName() {
        return "26.2 Fabric";
    }

    // --- world sharing (through the e4mc mod) -----------------------------------

    @Override
    public boolean inSingleplayer() {
        return mc.hasSingleplayerServer();
    }

    @Override
    public String shareUnavailableReason() {
        if (!mc.hasSingleplayerServer()) return "Open one of your singleplayer worlds, then share it from here.";
        if (!FabricLoader.getInstance().isModLoaded("e4mc"))
            return "The sharing mod (e4mc) isn't in this profile yet. Close Minecraft and press Play in SkyLaunch again; it's added automatically.";
        return null;
    }

    @Override
    public String startSharing() {
        // Minecraft's own Open to LAN screen. e4mc takes over from there and posts the address.
        try {
            Class<?> k = Class.forName("net.minecraft.client.gui.screens.ShareToLanScreen");
            Object screen = k.getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance((Object) null);
            mc.gui.setScreen((net.minecraft.client.gui.screens.Screen) screen);
            return null;
        } catch (Throwable t) {
            return "Press Esc and click Open to LAN instead.";
        }
    }

    @Override
    public boolean worldShared() {
        Object server = mc.getSingleplayerServer();
        if (server == null) return false;
        try {
            return (Boolean) server.getClass().getMethod("isPublished").invoke(server);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void copyToClipboard(String text) {
        mc.keyboardHandler.setClipboard(text);
    }

    @Override
    public String playerName() {
        return mc.getUser().getName();
    }

    @Override
    public File gameDir() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    /** GLFW key codes for the key names the shared code uses as defaults. */
    static final class Keys {
        static int glfw(String name) {
            String n = name.toUpperCase(Locale.ROOT);
            if (n.length() == 1) {
                char ch = n.charAt(0);
                if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) return ch; // GLFW uses ASCII here
            }
            switch (n) {
                case "RIGHT_SHIFT": return InputConstants.KEY_RSHIFT;
                case "LEFT_SHIFT": return InputConstants.KEY_LSHIFT;
                case "RIGHT_CONTROL": return InputConstants.KEY_RCONTROL;
                case "LEFT_ALT": return InputConstants.KEY_LALT;
                case "TAB": return InputConstants.KEY_TAB;
                default: return -1;
            }
        }
    }
}
