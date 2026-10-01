package robert_neat.his_backend.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Blad pola w odpowiedzi 422 (`FieldError` z kontraktu). */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record FieldError(String field, String message, String code) {

    public FieldError(String field, String message) {
        this(field, message, null);
    }
}
