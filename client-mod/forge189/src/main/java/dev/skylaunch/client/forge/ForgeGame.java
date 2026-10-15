package dev.skylaunch.client.forge;

import dev.skylaunch.client.Game;
import dev.skylaunch.client.ui.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Game data for Minecraft 1.8.9 (MCP names). */
final class ForgeGame implements Game {
    private final Minecraft mc = Minecraft.getMinecraft();

    public boolean inWorld() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    public int fps() {
        return Minecraft.getDebugFPS();
    }

    public int ping() {
        NetHandlerPlayClient net = mc.getNetHandler();
        if (net == null || mc.thePlayer == null) return -1;
        NetworkPlayerInfo info = net.getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? -1 : info.getResponseTime();
    }

    public double x() {
        return mc.thePlayer == null ? 0 : mc.thePlayer.posX;
    }

    public double y() {
        return mc.thePlayer == null ? 0 : mc.thePlayer.posY;
    }

    public double z() {
        return mc.thePlayer == null ? 0 : mc.thePlayer.posZ;
    }

    public float yaw() {
        return mc.thePlayer == null ? 0 : mc.thePlayer.rotationYaw;
    }

    public boolean sprinting() {
        return mc.thePlayer != null && mc.thePlayer.isSprinting();
    }

    public List<ItemInfo> armorAndHeld() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return Collections.emptyList();
        List<ItemInfo> out = new ArrayList<ItemInfo>();
        for (int i = 3; i >= 0; i--) add(out, p.inventory.armorInventory[i], false);
        add(out, p.getHeldItem(), true);
        return out;
    }

    private static void add(List<ItemInfo> out, ItemStack s, boolean held) {
        if (s == null) return;
        boolean dmg = s.isItemStackDamageable();
        out.add(new ItemInfo(s, dmg ? s.getItemDamage() : 0, dmg ? s.getMaxDamage() : 0, s.stackSize, held));
    }

    public List<EffectInfo> effects() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return Collections.emptyList();
        List<EffectInfo> out = new ArrayList<EffectInfo>();
        for (PotionEffect e : p.getActivePotionEffects()) {
            Potion potion = Potion.potionTypes[e.getPotionID()];
            String name = potion == null ? "Effect" : I18n.format(potion.getName());
            out.add(new EffectInfo(name, e.getAmplifier(), e.getIsPotionDurationMax() ? -1 : e.getDuration()));
        }
        return out;
    }

    public String scoreboardTitle() {
        if (mc.theWorld == null) return "";
        ScoreObjective o = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        return o == null ? "" : o.getDisplayName();
    }

    public List<String> scoreboardLines() {
        if (mc.theWorld == null) return Collections.emptyList();
        Scoreboard sb = mc.theWorld.getScoreboard();
        ScoreObjective o = sb.getObjectiveInDisplaySlot(1);
        if (o == null) return Collections.emptyList();
        Collection<Score> scores = sb.getSortedScores(o);
        List<String> lines = new ArrayList<String>();
        for (Score s : scores) {
            if (s.getPlayerName() == null || s.getPlayerName().startsWith("#")) continue;
            ScorePlayerTeam team = sb.getPlayersTeam(s.getPlayerName());
            lines.add(ScorePlayerTeam.formatPlayerName(team, s.getPlayerName()));
        }
        Collections.reverse(lines); // sorted ascending; the sidebar shows highest first
        return lines;
    }

    public List<String> tabLines() {
        NetHandlerPlayClient net = mc.getNetHandler();
        if (net == null) return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
            out.add(mc.ingameGUI.getTabList().getPlayerName(info));
        }
        return out;
    }

    public boolean keyDown(int keyCode) {
        return keyCode > 0 && keyCode < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(keyCode);
    }

    public String keyName(int keyCode) {
        if (keyCode <= 0) return "None";
        String n = Keyboard.getKeyName(keyCode);
        if (n == null) return "Key " + keyCode;
        if (n.equals("RSHIFT")) return "Right Shift";
        if (n.equals("LSHIFT")) return "Left Shift";
        if (n.equals("RCONTROL")) return "Right Ctrl";
        if (n.equals("LCONTROL")) return "Left Ctrl";
        if (n.equals("LMENU")) return "Left Alt";
        if (n.equals("RMENU")) return "Right Alt";
        return n.length() <= 1 ? n : n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }

    public int key(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        if (n.equals("RIGHT_SHIFT")) n = "RSHIFT";
        else if (n.equals("LEFT_SHIFT")) n = "LSHIFT";
        else if (n.equals("RIGHT_CONTROL")) n = "RCONTROL";
        else if (n.equals("LEFT_ALT")) n = "LMENU";
        int idx = Keyboard.getKeyIndex(n);
        return idx == Keyboard.KEY_NONE ? -1 : idx;
    }

    public boolean forwardDown() {
        return mc.gameSettings.keyBindForward.isKeyDown();
    }

    public boolean backDown() {
        return mc.gameSettings.keyBindBack.isKeyDown();
    }

    public boolean leftDown() {
        return mc.gameSettings.keyBindLeft.isKeyDown();
    }

    public boolean rightDown() {
        return mc.gameSettings.keyBindRight.isKeyDown();
    }

    public boolean jumpDown() {
        return mc.gameSettings.keyBindJump.isKeyDown();
    }

    public boolean attackDown() {
        return mc.gameSettings.keyBindAttack.isKeyDown();
    }

    public boolean useDown() {
        return mc.gameSettings.keyBindUseItem.isKeyDown();
    }

    public void openScreen(UiScreen screen) {
        mc.displayGuiScreen(new GuiWrapper(screen));
    }

    public void closeScreen() {
        mc.displayGuiScreen(null);
    }

    public File configDir() {
        return new File(mc.mcDataDir, "config");
    }

    public String platformName() {
        return "1.8.9 Forge";
    }

    // World sharing needs e4mc, which doesn't exist for 1.8.9.
    public boolean inSingleplayer() {
        return mc.isSingleplayer();
    }

    public String shareUnavailableReason() {
        return "Sharing worlds isn't available on 1.8.9. Use a modern (26.2) profile to share a world with friends.";
    }

    public String startSharing() {
        return shareUnavailableReason();
    }

    public boolean worldShared() {
        return false;
    }

    public void copyToClipboard(String text) {
        net.minecraft.client.gui.GuiScreen.setClipboardString(text);
    }

    public String playerName() {
        return mc.getSession().getUsername();
    }

    public File gameDir() {
        return mc.mcDataDir;
    }
}
