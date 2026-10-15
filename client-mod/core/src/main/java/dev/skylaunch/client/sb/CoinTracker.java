package dev.skylaunch.client.sb;

/**
 * Tracks purse changes over a session. The first reading is the baseline; every change after
 * that is added up. Switching profile (or leaving SkyBlock) pauses tracking until the purse is
 * seen again, so a different profile's purse never counts as a gain.
 */
public final class CoinTracker {
    private double last = Double.NaN;
    private String profile = null;
    private double net, gained;
    private long startedAt;

    public void update(double purse, String profileName, long now) {
        if (Double.isNaN(purse)) {
            last = Double.NaN;
            return;
        }
        String p = profileName == null ? "" : profileName;
        if (profile != null && !p.isEmpty() && !profile.isEmpty() && !p.equals(profile)) {
            // New profile: new baseline, keep the totals.
            last = Double.NaN;
        }
        if (!p.isEmpty()) profile = p;
        if (Double.isNaN(last)) {
            last = purse;
            if (startedAt == 0) startedAt = now;
            return;
        }
        double d = purse - last;
        last = purse;
        if (d == 0) return;
        net += d;
        if (d > 0) gained += d;
    }

    public boolean started() {
        return startedAt != 0;
    }

    public double net() {
        return net;
    }

    public double gained() {
        return gained;
    }

    /** Coins per hour since tracking started (at least one minute, so early numbers stay sane). */
    public double perHour(long now, boolean gainsOnly) {
        if (startedAt == 0) return 0;
        double hours = Math.max(60_000L, now - startedAt) / 3_600_000.0;
        return (gainsOnly ? gained : net) / hours;
    }

    public void reset(long now) {
        net = gained = 0;
        startedAt = Double.isNaN(last) ? 0 : now;
    }
}
