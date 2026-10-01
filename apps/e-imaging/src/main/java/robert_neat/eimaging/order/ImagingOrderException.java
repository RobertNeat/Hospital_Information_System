package robert_neat.eimaging.order;

/** Blad domenowy e-imaging; `kind` wyznacza kod HTTP w warstwie FHIR i komunikat w UI. */
public class ImagingOrderException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT, INVALID, REJECTED_BY_HIS }

    private final Kind kind;

    public ImagingOrderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
