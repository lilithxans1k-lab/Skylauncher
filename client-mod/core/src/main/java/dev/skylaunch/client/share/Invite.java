package dev.skylaunch.client.share;

import dev.skylaunch.client.config.Json;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Invite codes for shared worlds: "skylaunch:" + base64url(raw deflate(JSON)).
 * The JSON holds the address plus the host's mod list (written by the launcher into
 * skylaunch-share.json at launch), so a friend's launcher can install the same mods.
 * The launcher decodes these in src/core/share.js; keep the two in step.
 */
public final class Invite {
    public static final String PREFIX = "skylaunch:";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Pattern E4MC = Pattern.compile("([a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.e4mc\\.link)", Pattern.CASE_INSENSITIVE);

    private Invite() {}

    /** Finds an e4mc address in a chat message, or null. */
    public static String findAddress(String chat) {
        if (chat == null) return null;
        Matcher m = E4MC.matcher(chat);
        return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : null;
    }

    /**
     * Builds the invite. `manifest` is the parsed skylaunch-share.json (may be null, in which
     * case the invite only carries the address and version).
     */
    @SuppressWarnings("unchecked")
    public static String encode(String address, String host, Map<String, Object> manifest, String fallbackMc, String fallbackLoader) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("v", 1);
        m.put("a", address);
        m.put("h", host);
        m.put("mc", manifest != null && manifest.get("mc") != null ? manifest.get("mc") : fallbackMc);
        m.put("l", manifest != null && manifest.get("loader") != null ? manifest.get("loader") : fallbackLoader);
        if (manifest != null) {
            if (manifest.get("mods") instanceof List) m.put("m", manifest.get("mods"));
            if (manifest.get("files") instanceof List) m.put("f", manifest.get("files"));
        }
        byte[] raw = Json.write(m).replaceAll("\\n\\s*", "").getBytes(UTF8);
        Deflater d = new Deflater(9, true);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** Decodes an invite back to its JSON map (used by tests; the launcher has its own decoder). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> decode(String code) throws DataFormatException {
        String body = code.trim();
        if (body.startsWith(PREFIX)) body = body.substring(PREFIX.length());
        byte[] packed = java.util.Base64.getUrlDecoder().decode(body);
        Inflater inf = new Inflater(true);
        inf.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
            out.write(buf, 0, n);
        }
        inf.end();
        return (Map<String, Object>) Json.parse(new String(out.toByteArray(), UTF8));
    }
}
