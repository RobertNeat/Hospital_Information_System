package robert_neat.his_backend.common.api;

import java.util.List;

/** Konflikt stanu / unikalnosci / wersji (409 CONFLICT); opcjonalnie z `errors[]` wskazujacym pole. */
public class ConflictException extends RuntimeException {

    private final transient List<FieldError> errors;

    public ConflictException(String message) {
        this(message, List.of());
    }

    public ConflictException(String message, List<FieldError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
