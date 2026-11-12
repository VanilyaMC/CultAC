package ac.cult.cultac.events.packets;

import ac.cult.cultac.protocol.ProtocolVersion;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.Nullable;

/**
 * Reads ViaVersion's player details (ConnectionDetails#sendPlayerDetails): a UTF-8 JSON object whose {@code version}
 * is the original protocol of the client connected to the proxy.
 */
public final class ProxyClientDetails {
    public static final String CHANNEL = "vv:proxy_details";

    private ProxyClientDetails() {}

    /** The supported original protocol named by {@code data}; null when it names none or one CultAC cannot model. */
    public static @Nullable ProtocolVersion originalProtocol(@Nullable byte[] data) {
        if (data == null || data.length == 0 || data.length > 4096) return null;
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return null;
            JsonObject object = root.getAsJsonObject();
            JsonElement version = object.get("version");
            if (version == null
                    || !version.isJsonPrimitive()
                    || !version.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            double value = version.getAsDouble();
            if (value != Math.rint(value)) return null;
            return ProtocolVersion.of((int) value);
        } catch (RuntimeException invalid) { // JSON syntax or ProtocolResolutionException
            return null;
        }
    }
}
