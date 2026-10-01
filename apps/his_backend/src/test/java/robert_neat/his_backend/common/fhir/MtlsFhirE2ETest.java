package robert_neat.his_backend.common.fhir;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import robert_neat.his_backend.TestcontainersConfiguration;

/**
 * E2E mTLS na prawdziwych konektorach Tomcata (certyfikaty TEST-ONLY z {@link TestPki}, nie z `.certs`): /fhir/** tylko
 * przez HTTPS z certyfikatem klienta, /api/** tylko przez HTTP, health na porcie zarzadzania. Profil `mtls` aktywowany jawnie (testy domyslnie maja
 * HIS_MTLS_ENABLED=false); wlasny kontekst (osobny od {@code ApiIntegrationTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.liquibase.contexts=reference")
@ActiveProfiles("mtls")
@Import(TestcontainersConfiguration.class)
class MtlsFhirE2ETest {

    private static final TestPki PKI = TestPki.shared();
    private static final Path SERVER = PKI.issue("his-backend");
    private static final Path CLIENT = PKI.issue("e-receipt");
    private static final Path INTRUDER = PKI.issue("intruder");
    private static final Path FOREIGN_CA_CLIENT = TestPki.create("foreign-ca").issue("e-receipt");
    private static final int HTTP_PORT = freePort();
    private static final int MANAGEMENT_PORT = freePort();
    private static final String RX = "00000000-0000-0000-0000-000000000000";

    @LocalServerPort
    int httpsPort;
    @Value("${mtls.http-port}")
    int httpPort;

    @DynamicPropertySource
    static void mtlsProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> 0);
        registry.add("mtls.http-port", () -> HTTP_PORT);
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
        // 404 = autoryzacja przeszla (zasobu nie ma); 401 oznaczaloby odrzucenie CN
        HttpResponse<String> response = get("https://localhost:" + httpsPort + "/fhir/MedicationRequest/" + RX, CLIENT);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("OperationOutcome");
    }

    @Test
    void connectionWithoutClientCertificateIsRejected() {
        assertThatThrownBy(() -> get("https://localhost:" + httpsPort + "/fhir/MedicationRequest/" + RX, null))
                .isInstanceOf(IOException.class);
    }

    @Test
    void clientCertificateFromForeignCaIsRejected() {
        assertThatThrownBy(() -> get("https://localhost:" + httpsPort + "/fhir/MedicationRequest/" + RX,
                FOREIGN_CA_CLIENT)).isInstanceOf(IOException.class);
    }

    @Test
    void trustedCertificateWithUnlistedCnIsUnauthorized() throws Exception {
        assertThat(get("https://localhost:" + httpsPort + "/fhir/MedicationRequest/" + RX, INTRUDER).statusCode())
                .isEqualTo(401);
    }

    @Test
    void connectorsAreSeparatedByPath() throws Exception {
        assertThat(httpPort).isEqualTo(HTTP_PORT);
        // API tylko przez HTTP, /fhir tylko przez HTTPS
        assertThat(get("http://localhost:" + httpPort + "/api/v1/auth/register/wards", null).statusCode())
                .isEqualTo(200);
        assertThat(get("http://localhost:" + httpPort + "/fhir/MedicationRequest/" + RX, null).statusCode())
                .isEqualTo(404);
        assertThat(get("https://localhost:" + httpsPort + "/api/v1/auth/register/wards", CLIENT).statusCode())
                .isEqualTo(404);
    }

    @Test
    void healthIsServedOnPlainManagementPortOnly() throws Exception {
        assertThat(get("http://localhost:" + MANAGEMENT_PORT + "/actuator/health/readiness", null).statusCode())
                .isEqualTo(200);
        assertThat(get("http://localhost:" + httpPort + "/actuator/health/readiness", null).statusCode())
                .isNotEqualTo(200);
    }
}
