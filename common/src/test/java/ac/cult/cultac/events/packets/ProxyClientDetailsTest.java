package ac.cult.cultac.events.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ac.cult.cultac.protocol.ProtocolVersion;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProxyClientDetailsTest {
    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** The payload ViaVersion's Velocity platform sends for a 1.21.8 client (protocol 772). */
    @Test
    void readsTheOriginalProtocolOfViaPlayerDetails() {
        byte[] details = utf8("{\"specVersion\":1,\"platformName\":\"Velocity\",\"platformVersion\":\"3.4.0\","
                + "\"version\":772,\"versionName\":\"1.21.7-1.21.8\",\"versionType\":\"RELEASE\"}");

        assertEquals(ProtocolVersion.V1_21_7, ProxyClientDetails.originalProtocol(details));
    }

    @Test
    void readsTheCurrentVersion() {
        assertEquals(ProtocolVersion.V26_3, ProxyClientDetails.originalProtocol(utf8("{\"version\":777}")));
    }

    /** A version CultAC has no model for leaves the connection's own protocol in place. */
    @Test
    void ignoresUnsupportedProtocols() {
        assertNull(ProxyClientDetails.originalProtocol(utf8("{\"version\":47}")));
        assertNull(ProxyClientDetails.originalProtocol(utf8("{\"version\":767}")));
        assertNull(ProxyClientDetails.originalProtocol(utf8("{\"version\":999}")));
    }

    @Test
    void ignoresMalformedPayloads() {
        assertNull(ProxyClientDetails.originalProtocol(null));
        assertNull(ProxyClientDetails.originalProtocol(new byte[0]));
        assertNull(ProxyClientDetails.originalProtocol(utf8("not json")));
        assertNull(ProxyClientDetails.originalProtocol(utf8("[772]")));
        assertNull(ProxyClientDetails.originalProtocol(utf8("{\"versionName\":\"1.21.8\"}")));
        assertNull(ProxyClientDetails.originalProtocol(utf8("{\"version\":\"772\"}")));
        assertNull(ProxyClientDetails.originalProtocol(utf8("{\"version\":772.5}")));
    }
}
