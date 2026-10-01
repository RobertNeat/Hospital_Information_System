package robert_neat.his_backend.terminology.snomed;

/** Bazowy wyjatek domenowy klienta terminologii. */
public abstract class TerminologyException extends RuntimeException {

    protected TerminologyException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Terminology server niedostepny, wylaczony, timeout lub blad 5xx. */
    public static class Unavailable extends TerminologyException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Niepoprawne dane wejsciowe (lokalna walidacja lub odrzucony ECL). */
    public static class InvalidRequest extends TerminologyException {
        public InvalidRequest(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Pojecie nie istnieje w terminologii. */
    public static class NotFound extends TerminologyException {
        public NotFound(String message) {
            super(message, null);
        }
    }
}
