package robert_neat.ereceipt.prescription;

import java.util.Arrays;
import java.util.Optional;

import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus;

/**
 * Stan e-recepty. Kody `wire` sa zgodne ze statusami recept w HIS; `fhir` to standardowy status
 * R4 `MedicationRequest.status` (partially_dispensed nie ma odpowiednika, dlatego HIS dostaje dodatkowo rozszerzenie).
 */
public enum ReceiptStatus {
    ISSUED("issued", MedicationRequestStatus.ACTIVE),
    PARTIALLY_DISPENSED("partially_dispensed", MedicationRequestStatus.ACTIVE),
    DISPENSED("dispensed", MedicationRequestStatus.COMPLETED),
    CANCELLED("cancelled", MedicationRequestStatus.CANCELLED),
    EXPIRED("expired", MedicationRequestStatus.STOPPED);

    private final String wire;
    private final MedicationRequestStatus fhir;

    ReceiptStatus(String wire, MedicationRequestStatus fhir) {
        this.wire = wire;
        this.fhir = fhir;
    }

    public String wire() {
        return wire;
    }

    public MedicationRequestStatus fhir() {
        return fhir;
    }

    /** Recepta "zywa": mozna ja zrealizowac, anulowac albo uznac za wygasla. */
    public boolean isOpen() {
        return this == ISSUED || this == PARTIALLY_DISPENSED;
    }

    /** Dozwolone przejscia: z "zywej" recepty do dowolnego innego stanu poza `issued` i stanem biezacym. */
    public boolean canMoveTo(ReceiptStatus target) {
        return isOpen() && target != ISSUED && target != this;
    }

    public static Optional<ReceiptStatus> fromWire(String code) {
        return Arrays.stream(values()).filter(s -> s.wire.equals(code)).findFirst();
    }

    /** Odwzorowanie statusu FHIR bez rozszerzenia (partially_dispensed wymaga rozszerzenia). */
    public static Optional<ReceiptStatus> fromFhir(MedicationRequestStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case ACTIVE -> Optional.of(ISSUED);
            case COMPLETED -> Optional.of(DISPENSED);
            case CANCELLED -> Optional.of(CANCELLED);
            case STOPPED -> Optional.of(EXPIRED);
            default -> Optional.empty();
        };
    }
}
