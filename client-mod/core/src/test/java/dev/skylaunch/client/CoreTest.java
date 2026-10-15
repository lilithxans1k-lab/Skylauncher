package dev.skylaunch.client;

import dev.skylaunch.client.config.Json;
import dev.skylaunch.client.sb.SkyBlock;
import dev.skylaunch.client.util.Format;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Plain-Java checks for the shared code. Run: java -cp <classes> dev.skylaunch.client.CoreTest */
public final class CoreTest {
    private static int passed, failed;

    static void eq(Object expected, Object actual, String what) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (expected instanceof Double && actual instanceof Double)
            ok = expected.equals(actual) || Math.abs((Double) expected - (Double) actual) < 1e-6;
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what + ": expected <" + expected + "> got <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        format();
        json();
        scoreboard();
        tab();
        actionBar();
        coins();
        timestamps();
        invites();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void format() {
        eq("1,234,567", Format.full(1234567), "full");
        eq("12.5M", Format.compact(12_480_116), "compact M");
        eq("4.21k", Format.compact(4210), "compact k");
        eq("250M", Format.compact(250_000_000), "compact round");
        eq("950", Format.compact(950), "compact small");
        eq(1500000.0, Format.parse("1.5M"), "parse M");
        eq(12345.5, Format.parse("12,345.5"), "parse commas");
        eq("4m 05s", Format.duration(245), "duration");
        eq("1:30", Format.ticks(1800), "ticks");
        eq("Purse: 12", Format.strip("§6Purse: §e12"), "strip");
    }

    @SuppressWarnings("unchecked")
    static void json() {
        Object o = Json.parse("{\"a\": [1, 2.5, true, null], \"b\": {\"c\": \"x\\\"y\\u00e9\"}}");
        Map<String, Object> m = (Map<String, Object>) o;
        eq(4, ((List<Object>) m.get("a")).size(), "json array");
        eq("x\"yé", ((Map<String, Object>) m.get("b")).get("c"), "json string escapes");
        Object round = Json.parse(Json.write(o));
        eq(o.toString(), round.toString(), "json round trip");
    }

    static void scoreboard() {
        SkyBlock sb = new SkyBlock();
        List<String> lines = Arrays.asList(
                "§710/13/26 §8m42AB",
                " ",
                " Late Spring 13th",
                " §78:40pm §e☀",
                " §7⏣ §bVillage🍫",
                " ",
                "Purse: §612,480,116 §e(+5)",
                "Bits: §b4,210",
                " ",
                "§ewww.hypixel.net");
        sb.readScoreboard("§e§lSKYBLOCK §b§lCO-OP", lines);
        eq(true, sb.onSkyBlock, "on skyblock");
        eq(12480116.0, sb.purse, "purse");
        eq("Purse", sb.purseLabel, "purse label");
        eq(4210.0, sb.bits, "bits");
        eq("Village", sb.location, "location strips emoji");
        eq("m42AB", sb.server, "server");
        eq("Late Spring 13th", sb.date, "date");
        eq("8:40pm", sb.time, "time");
        eq(false, sb.night, "day");

        sb.readScoreboard("SKYBLOCK", Arrays.asList("Piggy: 1,000", "ф Wizard Tower", "Motes: 2,500"));
        eq("Piggy", sb.purseLabel, "piggy");
        eq(Double.NaN, sb.bits, "bits cleared when absent");
        eq("Wizard Tower", sb.location, "rift location");
        eq(2500.0, sb.motes, "motes");

        sb.readScoreboard("BED WARS", Arrays.asList("Purse: 5"));
        eq(false, sb.onSkyBlock, "not skyblock");
    }

    static void tab() {
        SkyBlock sb = new SkyBlock();
        sb.readTab(Arrays.asList("§b§lArea: §7Hub", " Server: §8mini12A", " Bank: §61.2B", "Profile: Apple ♻"));
        eq("Hub", sb.area, "area");
        eq("mini12A", sb.server, "tab server");
        eq(1.2e9, sb.bank, "bank");
        eq("Apple", sb.profile, "profile");
    }

    static void actionBar() {
        SkyBlock sb = new SkyBlock();
        long t0 = 1_000_000;
        sb.readActionBar("§c1,450/1,250❤     §a412§a❈ Defense     §b380/520✎ Mana", t0);
        eq(1450.0, sb.health, "health");
        eq(1250.0, sb.maxHealth, "max health");
        eq(412.0, sb.defense, "defense");
        eq(380.0, sb.mana, "mana");
        eq(520.0, sb.maxMana, "max mana");
        eq(true, sb.statsFresh(t0 + 1000), "fresh");

        sb.readActionBar("§c1,250/1,250❤   §3+28.5 Farming (12,345.5/50,000)   §b520/520✎ Mana §3120ʬ", t0 + 60_000);
        eq(120.0, sb.overflow, "overflow");
        SkyBlock.SkillTrack f = sb.skills.get("Farming");
        eq(12345.5, f.current, "skill current");
        eq(50000.0, f.needed, "skill needed");
        sb.readActionBar("+30 Farming (12,375.5/50,000)", t0 + 120_000);
        eq(58.5, f.sessionXp, "session xp");
        // 58.5 XP over the minimum 1-minute window -> 3510/h
        eq(58.5 / (60_000 / 3_600_000.0), f.perHour(t0 + 120_000), "xp per hour");
        sb.readActionBar("+5 Combat (45.2%)", t0 + 121_000);
        eq(45.2, sb.skills.get("Combat").percent, "percent form");
        eq("Combat", sb.lastSkill.name, "latest skill");
    }

    static void coins() {
        dev.skylaunch.client.sb.CoinTracker t = new dev.skylaunch.client.sb.CoinTracker();
        long t0 = 10_000_000;
        t.update(Double.NaN, "", t0);
        eq(false, t.started(), "not started without purse");
        t.update(1000, "Apple", t0);
        eq(true, t.started(), "started");
        t.update(1500, "Apple", t0 + 1000);
        t.update(1200, "Apple", t0 + 2000);
        eq(200.0, t.net(), "net after spend");
        eq(500.0, t.gained(), "gains only");
        t.update(50_000_000, "Banana", t0 + 3000);
        eq(200.0, t.net(), "profile switch is not a gain");
        t.update(50_000_100, "Banana", t0 + 4000);
        eq(300.0, t.net(), "tracks new profile");
        eq(300.0 / (60_000 / 3_600_000.0), t.perHour(t0 + 30_000, false), "per hour uses 1 minute minimum");
        t.reset(t0 + 5000);
        eq(0.0, t.net(), "reset");
    }

    static void timestamps() {
        dev.skylaunch.client.features.Extras.ChatTimestamps ts = new dev.skylaunch.client.features.Extras.ChatTimestamps();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(2026, 9, 15, 20, 41, 7);
        eq("[8:41 PM] ", ts.stamp(cal.getTime()), "12h stamp");
        ts.twentyFour.value = true;
        ts.seconds.value = true;
        eq("[20:41:07] ", ts.stamp(cal.getTime()), "24h stamp");
    }

    @SuppressWarnings("unchecked")
    static void invites() {
        eq("brave-otter.au.e4mc.link", dev.skylaunch.client.share.Invite.findAddress("Local game hosted on domain [Brave-Otter.au.e4mc.link]"), "find e4mc address");
        eq(null, dev.skylaunch.client.share.Invite.findAddress("Hello world"), "no address");
        java.util.Map<String, Object> manifest = new java.util.LinkedHashMap<String, Object>();
        manifest.put("mc", "26.2");
        manifest.put("loader", "fabric");
        java.util.List<Object> mods = new java.util.ArrayList<Object>();
        java.util.Map<String, Object> mod = new java.util.LinkedHashMap<String, Object>();
        mod.put("v", "AbC123xy");
        mod.put("t", "Sodium");
        mods.add(mod);
        manifest.put("mods", mods);
        manifest.put("files", java.util.Arrays.asList((Object) "MyWaypoints-1.2.jar"));
        String code = dev.skylaunch.client.share.Invite.encode("brave-otter.au.e4mc.link", "lilith_sb", manifest, "26.2", "fabric");
        eq(true, code.startsWith("skylaunch:"), "invite prefix");
        eq(true, code.length() < 300, "invite is short (" + code.length() + ")");
        try {
            java.util.Map<String, Object> back = dev.skylaunch.client.share.Invite.decode(code);
            eq("brave-otter.au.e4mc.link", back.get("a"), "invite address");
            eq("lilith_sb", back.get("h"), "invite host");
            eq("Sodium", ((java.util.Map<String, Object>) ((java.util.List<Object>) back.get("m")).get(0)).get("t"), "invite mods");
        } catch (Exception e) {
            eq("decoded", e.toString(), "invite decode");
        }
        if (System.getProperty("invite.out") != null) {
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("invite.out")), code.getBytes("UTF-8"));
            } catch (Exception ignored) {
                // optional cross-check with the launcher's decoder
            }
        }
    }
}
