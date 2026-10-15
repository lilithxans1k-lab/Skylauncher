package dev.skylaunch.client.ui;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.util.Draw;

import java.util.ArrayList;
import java.util.List;

/**
 * Share world: opens your singleplayer world to friends through e4mc and gives you an invite.
 * The invite carries your exact mod list, so friends on SkyLaunch get asked to install it.
 */
public final class ShareScreen extends UiScreen {
    private Theme t;
    private String error;
    private String copied;
    private long copiedAt;

    public ShareScreen(Client c) {
        super(c);
    }

    @Override
    protected void draw(Gfx g) {
        t = c.appearance.menuTheme();
        g.rect(0, 0, width, height, t.scrim);
        int pw = Math.min(340, width - 24);
        int x = (width - pw) / 2;
        List<String[]> lines = new ArrayList<String[]>(); // {text, colour key}
        String reason = c.game.shareUnavailableReason();
        String address = c.shareAddress;

        // Measure height first so the panel can be centred.
        int inner = pw - 32;
        int body;
        if (reason != null) body = wrapped(g, reason, inner) * 11 + 30;
        else if (address == null && !c.game.worldShared()) body = wrapped(g, intro(), inner) * 11 + 34;
        else if (address == null) body = 44;
        else body = 58 + wrapped(g, help(), inner) * 11;
        int ph = 40 + body + 16;
        int y = (height - ph) / 2;
        Draw.round(g, x, y, x + pw, y + ph, 4, t.bg);

        int cx = x + 16, cy = y + 14;
        g.text("Share world", cx, cy, t.text, false);
        closeButton(g, x + pw - 22, y + 10);
        cy += 20;
        g.rect(x, cy, x + pw, cy + 1, t.line);
        cy += 10;

        if (reason != null) {
            cy = paragraph(g, reason, cx, cy, inner, t.muted);
            cy += 6;
            button(g, "Close", cx, cy, 60, 18, false, new Click() {
                public void click(int mx, int my, int b) { c.game.closeScreen(); }
            });
        } else if (address == null && !c.game.worldShared()) {
            cy = paragraph(g, intro(), cx, cy, inner, t.muted);
            cy += 8;
            button(g, "Share world", cx, cy, 90, 18, true, new Click() {
                public void click(int mx, int my, int b) {
                    c.shareRequestedAt = System.currentTimeMillis();
                    error = c.game.startSharing();
                }
            });
            if (error != null) g.text(Draw.fit(g, error, inner - 100), cx + 100, cy + 5, t.danger, false);
        } else if (address == null) {
            int dots = (int) ((System.currentTimeMillis() / 400) % 4);
            g.text("Getting an address from e4mc" + "...".substring(0, dots), cx, cy + 2, t.muted, false);
            g.text("This takes a few seconds.", cx, cy + 16, t.faint, false);
        } else {
            g.text("Your world is open at", cx, cy, t.muted, false);
            cy += 12;
            g.push(cx, cy, 1.4f);
            g.text(address, 0, 0, t.accent, false);
            g.pop();
            cy += 18;
            boolean recent = System.currentTimeMillis() - copiedAt < 2000;
            final Client cc = c;
            button(g, recent && "invite".equals(copied) ? "Copied" : "Copy invite", cx, cy, 90, 18, true, new Click() {
                public void click(int mx, int my, int b) {
                    String inv = cc.invite();
                    if (inv != null) { cc.game.copyToClipboard(inv); copied = "invite"; copiedAt = System.currentTimeMillis(); }
                }
            });
            final String addr = address;
            button(g, recent && "address".equals(copied) ? "Copied" : "Copy address", cx + 98, cy, 90, 18, false, new Click() {
                public void click(int mx, int my, int b) {
                    cc.game.copyToClipboard(addr);
                    copied = "address";
                    copiedAt = System.currentTimeMillis();
                }
            });
            cy += 28;
            paragraph(g, help(), cx, cy, inner, t.muted);
        }
    }

    private String intro() {
        return "Friends can join this world from anywhere, on any launcher. You'll pick the game mode, then get an address and an invite to send them.";
    }

    private String help() {
        return "Friends on SkyLaunch: click Join a friend in the launcher and paste the invite. They'll be asked to install your mods. "
                + "Friends on other launchers: add the address as a server. They need Minecraft " + c.game.platformName().split(" ")[0]
                + " and the same mods. The world stays open while you're playing it.";
    }

    private int wrapped(Gfx g, String text, int w) {
        return wrap(g, text, w).size();
    }

    private List<String> wrap(Gfx g, String text, int w) {
        List<String> out = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = line.length() == 0 ? word : line + " " + word;
            if (g.textWidth(trial) > w && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private int paragraph(Gfx g, String text, int x, int y, int w, int colour) {
        for (String l : wrap(g, text, w)) {
            g.text(l, x, y, colour, false);
            y += 11;
        }
        return y;
    }

    private void button(Gfx g, String label, int x, int y, int w, int h, boolean primary, Click click) {
        boolean hov = hovered(x, y, x + w, y + h);
        int fill = primary ? (hov ? Draw.mix(t.accent, 0xFFFFFFFF, 0.12) : t.accent) : (hov ? t.selected : t.surface);
        Draw.round(g, x, y, x + w, y + h, 3, fill);
        if (!primary) Draw.outline(g, x, y, x + w, y + h, t.line);
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2, primary ? t.onAccent : t.text, false);
        hit("btn:" + label, x, y, x + w, y + h, click);
    }

    private void closeButton(Gfx g, int x, int y) {
        boolean hov = hovered(x, y, x + 12, y + 12);
        g.text("x", x + 3, y + 2, hov ? t.text : t.faint, false);
        hit("close", x, y, x + 12, y + 12, new Click() {
            public void click(int mx, int my, int b) { c.game.closeScreen(); }
        });
    }

    @Override
    protected boolean clickedNothing(int mx, int my, int button) {
        return false;
    }
}
