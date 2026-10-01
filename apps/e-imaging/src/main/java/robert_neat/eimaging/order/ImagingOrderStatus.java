package robert_neat.eimaging.order;

import java.util.Arrays;
import java.util.Optional;

import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus;

/**
 * Stan zlecenia. Kody `wire` i dozwolone przejscia sa zgodne z maszyna stanow zlecen obrazowych w HIS (dopuszczone
 * pominiecie `in_progress`: `scheduled -> completed`); `fhir` to status R4 `ServiceRequest.status` (etapy aktywnego
 * zlecenia -> `active`, dokladnosc w rozszerzeniu).
 */
public enum ImagingOrderStatus {
    ORDERED("ordered", ServiceRequestStatus.ACTIVE),
    SCHEDULED("scheduled", ServiceRequestStatus.ACTIVE),
    IN_PROGRESS("in_progress", ServiceRequestStatus.ACTIVE),
    COMPLETED("completed", ServiceRequestStatus.COMPLETED),
    CANCELLED("cancelled", ServiceRequestStatus.REVOKED);

    private final String wire;
    private final ServiceRequestStatus fhir;

    ImagingOrderStatus(String wire, ServiceRequestStatus fhir) {
        this.wire = wire;
        this.fhir = fhir;
    }

    public String wire() {
        return wire;
    }

    public ServiceRequestStatus fhir() {
        return fhir;
    }

    /** Wynik moze powstac, gdy badanie jest zaplanowane lub w toku (jak w HIS). */
    public boolean acceptsResults() {
        return this == SCHEDULED || this == IN_PROGRESS;
    }

    public boolean canMoveTo(ImagingOrderStatus target) {
        return switch (this) {
            case ORDERED -> target == SCHEDULED || target == IN_PROGRESS || target == CANCELLED;
            case SCHEDULED -> target == IN_PROGRESS || target == COMPLETED || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public static Optional<ImagingOrderStatus> fromWire(String code) {
        return Arrays.stream(values()).filter(s -> s.wire.equals(code)).findFirst();
    }

    /** Odwzorowanie statusu R4 bez rozszerzenia (`active` -> `ordered`; etapy wymagaja rozszerzenia). */
    public static Optional<ImagingOrderStatus> fromFhir(ServiceRequestStatus status) {
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
