package dev.skylaunch.client.ui;

import dev.skylaunch.client.Gfx;

/**
 * Tiny 9x9 icons drawn pixel by pixel, so they stay crisp at every GUI scale and look the same
 * on every Minecraft version. '#' is a filled pixel.
 */
public final class Icons {
    private Icons() {}

    private static final String[] ALL = {
            "###...###",
            "###...###",
            "###...###",
            ".........",
            ".........",
            ".........",
            "###...###",
            "###...###",
            "###...###"};
    private static final String[] HUD = {
            "#########",
            "#.......#",
            "#.##....#",
            "#.......#",
            "#....##.#",
            "#.......#",
            "#########",
            "...###...",
            "..#####.."};
    private static final String[] SKYBLOCK = {
            "....#....",
            "...###...",
            "..#####..",
            "....#....",
            "#########",
            ".#######.",
            "..#####..",
            "...###...",
            "....#...."};
    private static final String[] COMBAT = {
            "........#",
            ".......##",
            "......##.",
            ".....##..",
            "#...##...",
            ".#.##....",
            "..##.....",
            ".#.#.....",
            "#...#...."};
    private static final String[] VISUAL = {
            ".........",
            "..#####..",
            ".#.....#.",
            "#..###..#",
            "#..###..#",
            "#..###..#",
            ".#.....#.",
            "..#####..",
            "........."};
    private static final String[] APPEARANCE = {
            "...###...",
            ".##...##.",
            "#.#...#.#",
            "#.......#",
            "#..#....#",
            "#......##",
            ".#....#..",
            "..####...",
            "........."};
    private static final String[] SHARE = {
            "......##.",
            ".....####",
            "......##.",
            "....##...",
            ".##......",
            "####.....",
            ".##......",
            "....##...",
            "......##."};
    private static final String[] EDIT = {
            "......##.",
            ".....#..#",
            "....#..#.",
            "...#..#..",
            "..#..#...",
            ".#..#....",
            "#..#.....",
            "##.......",
            "###......"};

    public static void draw(Gfx g, String name, int x, int y, int argb) {
        String[] bmp;
        if ("all".equals(name)) bmp = ALL;
        else if ("hud".equals(name)) bmp = HUD;
        else if ("skyblock".equals(name)) bmp = SKYBLOCK;
        else if ("combat".equals(name)) bmp = COMBAT;
        else if ("visual".equals(name)) bmp = VISUAL;
        else if ("appearance".equals(name)) bmp = APPEARANCE;
        else if ("share".equals(name)) bmp = SHARE;
        else if ("edit".equals(name)) bmp = EDIT;
        else return;
        for (int row = 0; row < bmp.length; row++) {
            String line = bmp[row];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) != '#') { col++; continue; }
                int start = col;
                while (col < line.length() && line.charAt(col) == '#') col++;
                g.rect(x + start, y + row, x + col, y + row + 1, argb);
            }
        }
    }

    public static String forCategory(dev.skylaunch.client.module.Module.Category cat) {
        if (cat == null) return "all";
        switch (cat) {
            case HUD: return "hud";
            case SKYBLOCK: return "skyblock";
            case COMBAT: return "combat";
            case VISUAL: return "visual";
            default: return "appearance";
        }
    }
}
