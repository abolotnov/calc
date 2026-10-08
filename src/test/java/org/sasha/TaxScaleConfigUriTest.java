package org.sasha;

import org.sasha.util.ConfigReader;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sasha.exception.ConfigLoadException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * fromURI against a local self-signed HTTPS server; the JVM default SSLContext is swapped to trust it.
 */
class TaxScaleConfigUriTest {
    private static final String YAML = "bands:\n  - {from: 0, rate: 1.89}\n  - {from: 10000, rate: 2.5}\n";
    private static final String JSON = "{\"bands\": [{\"from\": 0, \"rate\": 1.89}, {\"from\": 10000, \"rate\": 2.5}]}";

    private static HttpsServer server;
    private static SSLContext originalContext;
    private static String base;

    @BeforeAll
    static void startServer(@TempDir Path dir) throws Exception {
        Path ks = dir.resolve("test.p12");
        char[] pass = "changeit".toCharArray();
        Process keytool = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "t", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "san=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", ks.toString(), "-storepass", "changeit")
                .redirectErrorStream(true).start();
        keytool.getInputStream().readAllBytes();
        assertEquals(0, keytool.waitFor(), "keytool failed");

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(ks.toFile())) {
            keyStore.load(in, pass);
        }
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("t", keyStore.getCertificate("t"));

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, pass);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        originalContext = SSLContext.getDefault();
        SSLContext.setDefault(ctx);

        server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ctx));
        respond("/bands.yaml", 200, YAML);
        respond("/bands.json", 200, JSON);
        respond("/missing", 404, "not found");
        respond("/empty", 200, "other: 1\n");
        respond("/bad-rate", 200, "bands:\n  - {from: 0, rate: abc}\n");
        server.start();
        base = "https://localhost:" + server.getAddress().getPort();
    }

    private static void respond(String path, int status, String body) {
        server.createContext(path, ex -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        SSLContext.setDefault(originalContext);
    }

    @Test
    void loadsYaml() {
        var scale = ConfigReader.fromURI(URI.create(base + "/bands.yaml")).getScale();
        assertEquals(2, scale.size());
        assertEquals(new BigDecimal("1.89"), scale.get(BigDecimal.ZERO));
        assertEquals(new BigDecimal("2.5"), scale.get(new BigDecimal("10000")));
    }

    @Test
    void loadsJson() {
        var scale = ConfigReader.fromURI(URI.create(base + "/bands.json")).getScale();
        assertEquals(new BigDecimal("1.89"), scale.get(BigDecimal.ZERO));
        assertEquals(new BigDecimal("2.5"), scale.get(new BigDecimal("10000")));
    }

    @Test
    void rejectsNonHttps() {
        assertThrows(ConfigLoadException.class, () -> ConfigReader.fromURI(URI.create("http://localhost/bands.yaml")));
    }

    @Test
    void rejectsNon200() {
        assertThrows(ConfigLoadException.class, () -> ConfigReader.fromURI(URI.create(base + "/missing")));
    }

    @Test
    void rejectsDocumentWithoutBands() {
        assertThrows(ConfigLoadException.class, () -> ConfigReader.fromURI(URI.create(base + "/empty")));
    }

    @Test
    void rejectsNonNumericRate() {
        assertThrows(ConfigLoadException.class, () -> ConfigReader.fromURI(URI.create(base + "/bad-rate")));
    }

    @Test
    void rejectsUnreachableHost() throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        assertThrows(ConfigLoadException.class, () -> ConfigReader.fromURI(URI.create("https://localhost:" + port + "/bands.yaml")));
    }
}
