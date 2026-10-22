package dev.grokhome;

import java.util.Locale;
import okhttp3.HttpUrl;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Chip state comes from the saved computer URL plus a /health probe.
 * A tailnet host (*.ts.net or 100.64.0.0/10) that answers is "Away · private",
 * including on the home couch. TRANSPORT_VPN is not the chip: any VPN would
 * look like Tailscale, and a LAN URL does not use the tunnel.
 */
public final class ConnectionRules {
    public enum Mode { CONNECTED, AWAY, OFFLINE }
    public enum HostKind { LAN, TAILNET, LOOPBACK, OTHER }

    public static final String PORT_HINT = "Add a port, like 100.101.12.34:8787";
    public static final String OFFLINE_TITLE = "Can't reach your computer";
    public static final String OFFLINE_SAVED = "Your saved chats are still here";
    public static final String OFFLINE_ASLEEP = "Is it asleep?";
    static final long RETRY_CAP_MS = 30_000L;

    private ConnectionRules() {}

    /** Tailscale CGNAT 100.64.0.0/10. Literal octets only, no DNS. */
    public static boolean isTailscaleIpv4(String host) {
        if (host == null) return false;
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) return false;
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) return false;
        }
        return octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127;
    }

    public static HostKind hostKind(String baseUrl) {
        HttpUrl url = HttpUrl.parse(baseUrl == null ? "" : baseUrl.trim());
        if (url == null) return HostKind.OTHER;
        String host = url.host();
        if (isLoopback(host)) return HostKind.LOOPBACK;
        if (LocalNetwork.isPrivateIpv4(host)) return HostKind.LAN;
        if (host.endsWith(".ts.net") || isTailscaleIpv4(host)) return HostKind.TAILNET;
        return HostKind.OTHER;
    }

    /**
     * @param validatedNetwork a network with INTERNET and NET_CAPABILITY_VALIDATED
     * @param healthOk /health returned the voice service
     */
    public static Mode map(boolean validatedNetwork, boolean healthOk, HostKind kind) {
        if (!validatedNetwork || !healthOk) return Mode.OFFLINE;
        if (kind == HostKind.TAILNET) return Mode.AWAY;
        return Mode.CONNECTED;
    }

    public static String chipLabel(Mode mode) {
        switch (mode) {
            case CONNECTED: return "Connected";
            case AWAY: return "Away · private";
            case OFFLINE: return "Offline · retrying";
            default:
                throw new AssertionError(mode);
        }
    }

    public static int dotColor(Mode mode) {
        switch (mode) {
            case CONNECTED: return 0xff3ddc84;
            case AWAY: return 0xff7eb6ff;
            case OFFLINE: return 0xfff4a62a;
            default:
                throw new AssertionError(mode);
        }
    }

    /** Consecutive failures, starting at 1. 2s, 4s, 8s, 16s, then 30s. */
    public static long retryDelayMs(int consecutiveFailures) {
        if (consecutiveFailures <= 0) return 0;
        int shift = Math.min(consecutiveFailures - 1, 4);
        long delay = 2000L << shift;
        return Math.min(RETRY_CAP_MS, delay);
    }

    public static String retryCountdown(long remainingMs) {
        long seconds = Math.max(0, (remainingMs + 999) / 1000);
        if (seconds == 1) return "Retrying in 1 second";
        return "Retrying in " + seconds + " seconds";
    }

    /**
     * Compact phrase for the connected card. Under 10 seconds is "just now".
     * After that the wording matches the task status Seen line: "Seen 3 min ago",
     * "Seen 1 hr ago". Negative age means never.
     */
    public static String syncCompact(long ageMs) {
        if (ageMs < 0) return "not synced yet";
        long seconds = ageMs / 1000;
        if (seconds < 10) return "just now";
        long minutes = seconds / 60;
        if (minutes < 1) return "Seen 1 min ago";
        if (minutes < 60) return "Seen " + minutes + " min ago";
        long hours = minutes / 60;
        if (hours == 1) return "Seen 1 hr ago";
        return "Seen " + hours + " hr ago";
    }

    /** Sentence for the offline card: "Last synced 4 minutes ago". Negative age means never. */
    public static String syncSentence(long ageMs) {
        if (ageMs < 0) return "Not synced yet";
        long seconds = Math.max(1, ageMs / 1000);
        if (seconds < 60) return "Last synced " + seconds + (seconds == 1 ? " second ago" : " seconds ago");
        long minutes = seconds / 60;
        if (minutes < 60) return "Last synced " + minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        return "Last synced " + hours + (hours == 1 ? " hour ago" : " hours ago");
    }

    public static final class Address {
        public final String stored;
        public final String message;
        Address(String stored, String message) { this.stored = stored; this.message = message; }
        public boolean ok() { return message == null; }
    }

    /**
     * Blur validation. A bare tailnet host is stored as HTTPS, because HTTP to
     * 100.64.0.0/10 is rejected by {@link VoiceClient#sessionUrl}. A bare LAN
     * host is stored as HTTP.
     */
    public static Address check(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new Address(null, PORT_HINT);
        String text = raw.trim();
        boolean hasScheme = text.regionMatches(true, 0, "https://", 0, 8) || text.regionMatches(true, 0, "http://", 0, 7);
        String body = text.replaceFirst("(?i)^https?://", "");
        if (body.contains("/") || body.contains("?") || body.contains("#")) return new Address(null, "Enter the server address without a path");
        String hostport = body;
        if (hostport.startsWith("[") || hostport.chars().filter(ch -> ch == ':').count() > 1) return new Address(null, PORT_HINT);
        int colon = hostport.lastIndexOf(':');
        if (colon <= 0 || colon == hostport.length() - 1) return new Address(null, PORT_HINT);
        String portText = hostport.substring(colon + 1);
        if (!portText.matches("[0-9]{1,5}")) return new Address(null, PORT_HINT);
        int port = Integer.parseInt(portText);
        if (port < 1 || port > 65535) return new Address(null, PORT_HINT);
        String host = hostport.substring(0, colon);
        if (host.isEmpty()) return new Address(null, PORT_HINT);
        String scheme;
        if (hasScheme) scheme = text.toLowerCase(Locale.US).startsWith("https") ? "https" : "http";
        else if (isLoopback(host) || LocalNetwork.isPrivateIpv4(host)) scheme = "http";
        else scheme = "https";
        String stored = scheme + "://" + host + ":" + port;
        try {
            VoiceClient.sessionUrl(stored);
        } catch (IllegalArgumentException error) {
            return new Address(null, error.getMessage());
        }
        return new Address(stored, null);
    }

    public static String display(String stored) {
        HttpUrl url = HttpUrl.parse(stored == null ? "" : stored.trim());
        if (url == null) return stored == null ? "" : stored;
        return url.host() + ":" + url.port();
    }

    /** Unfocused address field. An IPv4 host keeps the first octet: 100.•••.•••.•••:8787. */
    public static String mask(String shown) {
        if (shown == null || shown.isEmpty()) return "";
        int colon = shown.lastIndexOf(':');
        String host = colon > 0 ? shown.substring(0, colon) : shown;
        String port = colon > 0 ? shown.substring(colon) : "";
        String[] parts = host.split("\\.", -1);
        boolean ipv4 = parts.length == 4;
        if (ipv4) {
            for (String part : parts) {
                if (!part.matches("[0-9]{1,3}")) { ipv4 = false; break; }
            }
        }
        if (ipv4) return parts[0] + ".•••.•••.•••" + port;
        if (host.length() <= 4) return "•••" + port;
        return host.substring(0, 2) + "•••" + host.substring(host.length() - 2) + port;
    }

    public static int port(String stored) {
        HttpUrl url = HttpUrl.parse(stored == null ? "" : stored.trim());
        return url == null ? 0 : url.port();
    }

    public static final class Health {
        public final boolean answering;
        public final String version;
        /** Unix epoch milliseconds, or 0 when the server omits started_at. */
        public final long startedAtMillis;
        Health(boolean answering, String version, long startedAtMillis) {
            this.answering = answering;
            this.version = version;
            this.startedAtMillis = startedAtMillis;
        }

        public static Health parse(int code, String body) {
            if (code != 200 || body == null || body.isEmpty()) return new Health(false, "", 0);
            try {
                JSONObject json = new JSONObject(body);
                boolean answering = "grok-home-voice".equals(json.optString("service"));
                String version = json.optString("version", "");
                long started = json.optLong("started_at", 0);
                if (started > 0 && started < 10_000_000_000L) started *= 1000L;
                return new Health(answering, version, started);
            } catch (JSONException error) {
                return new Health(false, "", 0);
            }
        }
    }

    private static boolean isLoopback(String host) {
        return "127.0.0.1".equals(host) || "localhost".equals(host) || "10.0.2.2".equals(host);
    }
}
