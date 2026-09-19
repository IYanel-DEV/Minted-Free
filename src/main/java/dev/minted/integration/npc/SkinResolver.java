package dev.minted.integration.npc;

import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Resolves a real player's signed texture pair from Mojang's session server.
 *
 * <p>A skin carries far more weight than an ordinary head texture: the client
 * only trusts textures whose signature comes from Mojang, so a MinecraftHeads
 * value on a fake player renders as nothing (Steve/Alex) on some clients.
 * Fetching {@code value} + {@code signature} from Minecraft's own API for a
 * real player's skin guarantees the teller always shows that skin. Runs off
 * the main thread; any failure simply falls back to the teller's default.
 */
public final class SkinResolver {

    private static final int TIMEOUT_MS = 6000;

    public interface Callback {
        void resolved(String value, String signature);
    }

    private SkinResolver() {
    }

    public static void resolve(final Plugin plugin, final String playerName, final Callback callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                String value = null;
                String signature = null;
                try {
                    String uuidJson = get("https://api.mojang.com/users/profiles/minecraft/"
                            + playerName.toLowerCase(Locale.ROOT));
                    String id = dashed("id", uuidJson);
                    if (id == null) {
                        throw new IOException("no uid for " + playerName);
                    }
                    String profileJson = get("https://sessionserver.mojang.com/session/minecraft/profile/"
                            + id + "?unsigned=false");
                    value = property(profileJson, "value");
                    signature = property(profileJson, "signature");
                } catch (Throwable failure) {
                    // Offline server or unknown name: the caller keeps the default skin.
                    value = null;
                    signature = null;
                }
                callback.resolved(value, signature);
            }
        });
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "Minted/bank-npcs");
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Reads {@code "key":"...","..."} and reformats a 32-char id as a dashed UUID. */
    private static String dashed(String key, String json) {
        String raw = property(key, json);
        if (raw == null || raw.length() != 32) {
            return null;
        }
        return raw.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
    }

    /** First {@code "key":"<value>"} after the textures property in a session profile. */
    private static String property(String json, String key) {
        int textures = json.indexOf("\"name\":\"textures\"");
        int start = textures < 0 ? 0 : textures;
        String prefix = "\"" + key + "\":\"";
        int at = json.indexOf(prefix, start);
        if (at < 0) {
            return null;
        }
        int valueStart = at + prefix.length();
        int end = json.indexOf('"', valueStart);
        return end < 0 ? null : json.substring(valueStart, end);
    }
}