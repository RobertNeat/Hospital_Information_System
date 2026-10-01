package robert_neat.his_backend.common.wire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.fasterxml.jackson.annotation.JsonInclude;

import robert_neat.his_backend.common.api.ApiErrorCode;
import robert_neat.his_backend.common.api.ApiProblem;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.wire.WireSamples.Blood;
import robert_neat.his_backend.common.wire.WireSamples.Coding;
import robert_neat.his_backend.common.wire.WireSamples.Modality;
import robert_neat.his_backend.common.wire.WireSamples.OrderStatus;
import robert_neat.his_backend.common.wire.WireSamples.Payload;
import robert_neat.his_backend.common.wire.WireSamples.Reimbursement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Serializacja przez mapper zbudowany przez Spring Boot (application.properties), nie `new JsonMapper()`. */
@JsonTest
class WireEnumJsonTest {

    @Autowired
    private JsonMapper mapper;

    record Sample(UUID id, Instant at, LocalDate day, String email,
            @JsonInclude(JsonInclude.Include.ALWAYS) String pesel, boolean online, List<String> tags) {
    }

    @Test
    void enumsSerializeToContractWireValues() throws Exception {
        Payload p = new Payload(Reimbursement.PERCENT_100, Blood.BLOOD_0_PLUS, Coding.ICD_10, Modality.USG,
                OrderStatus.SPECIMEN_COLLECTED);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(p));
        assertThat(json.get("reimbursement").asString()).isEqualTo("100%");
        assertThat(json.get("blood").asString()).isEqualTo("0+");
        assertThat(json.get("coding").asString()).isEqualTo("ICD-10");
        assertThat(json.get("modality").asString()).isEqualTo("USG");
        assertThat(json.get("status").asString()).isEqualTo("specimen_collected");
    }

    @Test
    void enumsDeserializeFromWireValues() throws Exception {
        Payload p = mapper.readValue("""
                {"reimbursement":"50%","blood":"AB-","coding":"ICD-9-PL","modality":"RTG","status":"ordered"}
                """, Payload.class);
        assertThat(p).isEqualTo(new Payload(Reimbursement.PERCENT_50, Blood.AB_MINUS, Coding.ICD_9_PL,
                Modality.RTG, OrderStatus.ORDERED));
    }

    @Test
    void javaConstantNameIsNotAcceptedOnTheWire() {
        assertThatThrownBy(() -> mapper.readValue("{\"reimbursement\":\"PERCENT_100\"}", Payload.class))
                .isInstanceOf(tools.jackson.databind.exc.InvalidFormatException.class);
    }

    @Test
    void instantIsIsoWithZ_localDateIso_nullsOmittedExceptAlways() throws Exception {
        Sample s = new Sample(UUID.fromString("16259545-f97c-531d-b9cd-6ba115379372"),
                Instant.parse("2026-01-31T10:15:00Z"), LocalDate.of(2026, 1, 31), null, null, false, null);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(s));
        assertThat(json.get("at").asString()).isEqualTo("2026-01-31T10:15:00Z");
        assertThat(json.get("day").asString()).isEqualTo("2026-01-31");
        assertThat(json.get("id").asString()).isEqualTo("16259545-f97c-531d-b9cd-6ba115379372");
        assertThat(json.has("email")).isFalse();
        assertThat(json.has("tags")).isFalse();
        assertThat(json.has("pesel")).isTrue();
        assertThat(json.get("pesel").isNull()).isTrue();
        assertThat(json.get("online").asBoolean()).isFalse();
    }

    @Test
    void problemDetailHasContractShape() throws Exception {
        var pd = ApiProblem.validation("Walidacja nie powiodla sie",
                List.of(new FieldError("role", "zla wartosc"), new FieldError("size", "za duze", "Max")));
        pd.setInstance(java.net.URI.create("/api/v1/staff"));
        JsonNode json = mapper.readTree(mapper.writeValueAsString(pd));
        assertThat(json.get("type").asString()).isEqualTo("urn:his:problem:validation-failed");
        assertThat(json.get("title").asString()).isEqualTo("Unprocessable Content");
        assertThat(json.get("status").asInt()).isEqualTo(422);
        assertThat(json.get("detail").asString()).isEqualTo("Walidacja nie powiodla sie");
        assertThat(json.get("instance").asString()).isEqualTo("/api/v1/staff");
        assertThat(json.get("code").asString()).isEqualTo(ApiErrorCode.VALIDATION_FAILED.name());
        assertThat(json.get("errors")).hasSize(2);
        assertThat(json.get("errors").get(0).get("field").asString()).isEqualTo("role");
        assertThat(json.get("errors").get(0).has("code")).isFalse();
        assertThat(json.get("errors").get(1).get("code").asString()).isEqualTo("Max");
        assertThat(json.has("properties")).isFalse();
    }

    @Test
    void apiErrorCodesMatchContractUnion() {
        assertThat(ApiErrorCode.values()).extracting(Enum::name).containsExactlyInAnyOrder(
                "NOT_FOUND", "VALIDATION_FAILED", "CONFLICT", "FORBIDDEN", "UNAUTHENTICATED", "INTERNAL");
    }

    @Test
    void pageResponseHasContractShape() throws Exception {
        var page = new PageResponse<>(List.of("a", "b"), 1, 2, 5L, 3);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(page));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("items", "page", "size", "totalElements",
                "totalPages");
        assertThat(json.get("items")).hasSize(2);
        assertThat(json.get("page").asInt()).isEqualTo(1);
        assertThat(json.get("size").asInt()).isEqualTo(2);
        assertThat(json.get("totalElements").asLong()).isEqualTo(5L);
        assertThat(json.get("totalPages").asInt()).isEqualTo(3);
    }
}
