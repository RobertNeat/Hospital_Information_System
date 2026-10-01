package robert_neat.ereceipt.mtls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * E2E mTLS e-receipt na prawdziwych konektorach (certyfikaty TEST-ONLY z {@link TestPki}): FHIR tylko przez HTTPS
 * z certyfikatem klienta zaufanego CA, UI tylko przez HTTP, health na porcie zarzadzania.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mtls")
class MtlsE2ETest {

    private static final TestPki PKI = TestPki.shared();
    private static final Path SERVER = PKI.issue("e-receipt");
    private static final Path CLIENT = PKI.issue("his-backend");
    private static final Path FOREIGN_CA_CLIENT = TestPki.create("foreign-ca").issue("his-backend");
    private static final int UI_PORT = freePort();
    private static final int MANAGEMENT_PORT = freePort();
    private static final String ID = "00000000-0000-0000-0000-000000000000";

    @LocalServerPort
    int httpsPort;

    @DynamicPropertySource
    static void mtlsProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> 0);
        registry.add("mtls.http-port", () -> UI_PORT);
        registry.add("management.server.port", () -> MANAGEMENT_PORT);
        registry.add("spring.ssl.bundle.jks.mtls.key.alias", () -> "entity");
        registry.add("spring.ssl.bundle.jks.mtls.keystore.location", () -> "file:" + SERVER);
        registry.add("spring.ssl.bundle.jks.mtls.keystore.password", () -> TestPki.PASSWORD);
        registry.add("spring.ssl.bundle.jks.mtls.truststore.location", () -> "file:" + PKI.trustStore());
        registry.add("spring.ssl.bundle.jks.mtls.truststore.password", () -> TestPki.PASSWORD);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpResponse<String> get(String url, Path clientStore) throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
        if (url.startsWith("https")) {
            builder.sslContext(TestPki.sslContext(clientStore, PKI));
        }
        return builder.build().send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void validClientCertificateReachesFhirEndpoint() throws Exception {
        // 404 = zadanie dotarlo do kontrolera (recepty nie ma)
        assertThat(get("https://localhost:" + httpsPort + "/fhir/MedicationRequest/" + ID, CLIENT).statusCode())
                .isEqualTo(404);
    }

    @Test
    void connectionWithoutClientCertificateIsRejected() {
        assertThatThrownBy(() -> get("https://localhost:" + httpsPort + "/fhir/MedicationRequest/" + ID, null))
                .isInstanceOf(IOException.class);
    }

    @Test
    void clientCertificateFromForeignCaIsRejected() {
        assertThatThrownBy(() -> get("https://localhost:" + httpsPort + "/fhir/MedicationRequest/" + ID,
                FOREIGN_CA_CLIENT)).isInstanceOf(IOException.class);
    }

    @Test
    void uiIsPlainHttpOnlyAndFhirIsNotExposedThere() throws Exception {
        assertThat(get("http://localhost:" + UI_PORT + "/ui/prescriptions", null).statusCode()).isEqualTo(200);
        assertThat(get("http://localhost:" + UI_PORT + "/fhir/MedicationRequest/" + ID, null).statusCode())
                .isEqualTo(404);
        assertThat(get("https://localhost:" + httpsPort + "/ui/prescriptions", CLIENT).statusCode()).isEqualTo(404);
    }

    @Test
    void healthIsServedOnPlainManagementPortOnly() throws Exception {
        assertThat(get("http://localhost:" + MANAGEMENT_PORT + "/actuator/health/readiness", null).statusCode())
                .isEqualTo(200);
        assertThat(get("http://localhost:" + UI_PORT + "/actuator/health/readiness", null).statusCode())
                .isEqualTo(404);
    }
}
