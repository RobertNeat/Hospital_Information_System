package robert_neat.his_backend.lab.elab;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class ELabClientTest {

    private static final String BASE = "http://e-laboratory.test/fhir";

    private MockRestServiceServer server;
    private ELabClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ELabClient(builder.build());
    }

    @Test
    void submitPostsFhirJson() {
        server.expect(requestTo(BASE + "/ServiceRequest")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/fhir+json"))
                .andExpect(content().string("{\"resourceType\":\"ServiceRequest\"}"))
                .andRespond(withStatus(HttpStatus.CREATED));

        client.submit("{\"resourceType\":\"ServiceRequest\"}");
        server.verify();
    }

    @Test
    void updateStatusPutsToOrderId() {
        server.expect(requestTo(BASE + "/ServiceRequest/ORD1")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        client.updateStatus("ORD1", "{}");
        server.verify();
    }

    @Test
    void httpErrorsPropagateAsClientExceptions() {
        server.expect(requestTo(BASE + "/ServiceRequest/ORD1")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.updateStatus("ORD1", "{}")).isInstanceOf(HttpClientErrorException.class);
    }
}
