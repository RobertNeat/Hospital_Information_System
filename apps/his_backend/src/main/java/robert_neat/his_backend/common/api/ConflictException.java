package robert_neat.his_backend.common.api;

/** Konflikt stanu / unikalnosci / wersji (409 CONFLICT). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
