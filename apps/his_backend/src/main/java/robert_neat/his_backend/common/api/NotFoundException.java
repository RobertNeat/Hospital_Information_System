package robert_neat.his_backend.common.api;

/** Zasob nie istnieje (404 NOT_FOUND). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " o identyfikatorze '" + id + "' nie istnieje");
    }
}
