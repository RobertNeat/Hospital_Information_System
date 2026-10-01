package robert_neat.his_backend.common.api;

/** Operacja niedozwolona w biezacym stanie (np. konto oczekujace/zablokowane) - 403 FORBIDDEN. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
