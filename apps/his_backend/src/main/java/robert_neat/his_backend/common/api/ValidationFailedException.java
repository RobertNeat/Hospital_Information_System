package robert_neat.his_backend.common.api;

import java.util.List;

/** Blad walidacji wykryty w warstwie aplikacji (422 VALIDATION_FAILED z `errors[]`). */
public class ValidationFailedException extends RuntimeException {

    private final transient List<FieldError> errors;

    public ValidationFailedException(List<FieldError> errors) {
        super("Walidacja nie powiodla sie");
        this.errors = List.copyOf(errors);
    }

    public ValidationFailedException(String field, String message) {
        this(List.of(new FieldError(field, message)));
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
