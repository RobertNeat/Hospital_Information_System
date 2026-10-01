package robert_neat.elaboratory.order;

import java.util.Arrays;
import java.util.Optional;

import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus;

/**
 * Stan zlecenia. Kody `wire` i dozwolone przejscia sa zgodne z maszyna stanow zlecen laboratoryjnych w HIS
 * (z pominieciem etapow pomocniczych: `ordered -> specimen_collected`, `specimen_collected -> completed`);
 * `fhir` to status R4 `ServiceRequest.status` (etapy aktywnego zlecenia -> `active`, dokladnosc w rozszerzeniu).
 */
public enum LabOrderStatus {
    ORDERED("ordered", ServiceRequestStatus.ACTIVE),
    SCHEDULED("scheduled", ServiceRequestStatus.ACTIVE),
    SPECIMEN_COLLECTED("specimen_collected", ServiceRequestStatus.ACTIVE),
    IN_PROGRESS("in_progress", ServiceRequestStatus.ACTIVE),
    COMPLETED("completed", ServiceRequestStatus.COMPLETED),
    CANCELLED("cancelled", ServiceRequestStatus.REVOKED);

    private final String wire;
    private final ServiceRequestStatus fhir;

    LabOrderStatus(String wire, ServiceRequestStatus fhir) {
        this.wire = wire;
        this.fhir = fhir;
    }

    public String wire() {
        return wire;
    }

    public ServiceRequestStatus fhir() {
        return fhir;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Wynik moze powstac, gdy material jest pobrany (po `completed` tylko korekta). */
    public boolean acceptsResults() {
        return this == SPECIMEN_COLLECTED || this == IN_PROGRESS;
    }

    public boolean canMoveTo(LabOrderStatus target) {
        return switch (this) {
            case ORDERED -> target == SCHEDULED || target == SPECIMEN_COLLECTED || target == CANCELLED;
            case SCHEDULED -> target == SPECIMEN_COLLECTED || target == CANCELLED;
            case SPECIMEN_COLLECTED -> target == IN_PROGRESS || target == COMPLETED || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public static Optional<LabOrderStatus> fromWire(String code) {
        return Arrays.stream(values()).filter(s -> s.wire.equals(code)).findFirst();
    }

    /** Odwzorowanie statusu R4 bez rozszerzenia (`active` -> `ordered`; etapy wymagaja rozszerzenia). */
    public static Optional<LabOrderStatus> fromFhir(ServiceRequestStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case ACTIVE -> Optional.of(ORDERED);
            case COMPLETED -> Optional.of(COMPLETED);
            case REVOKED -> Optional.of(CANCELLED);
            default -> Optional.empty();
        };
    }
}
