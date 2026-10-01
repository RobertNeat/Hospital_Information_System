package robert_neat.ereceipt.prescription;

/** Blad domenowy e-receipt; `kind` wyznacza kod HTTP w warstwie FHIR i komunikat w UI. */
public class ReceiptException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT, INVALID, REJECTED_BY_HIS }

    private final Kind kind;

    public ReceiptException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
