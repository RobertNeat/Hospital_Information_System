package robert_neat.elaboratory.order;

/** Blad domenowy e-laboratory; `kind` wyznacza kod HTTP w warstwie FHIR i komunikat w UI. */
public class LabOrderException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT, INVALID, REJECTED_BY_HIS }

    private final Kind kind;

    public LabOrderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
