package robert_neat.his_backend.imaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.order.StatusChangeResponse;
import robert_neat.his_backend.common.order.Urgency;
import robert_neat.his_backend.ehr.Coding;

/** ImagingOrder z kontraktu (pola opcjonalne pomijane, gdy brak; historia statusow rosnaco po `at`). */
public record ImagingOrderResponse(
        UUID id,
        UUID patientId,
        UUID encounterId,
        String examCode,
        String examName,
        ImagingModality modality,
        String bodyRegion,
        Laterality laterality,
        boolean contrast,
        String clinicalIndication,
        String clinicalQuestion,
        Coding diagnosisCode,
        Urgency urgency,
        Safety safety,
        UUID slotId,
        Instant scheduledAt,
        UUID orderedById,
        Instant orderedAt,
        OrderStatus status,
        List<StatusChangeResponse> statusHistory,
        Instant createdAt,
        UUID createdById,
        Instant updatedAt,
        UUID updatedById,
        long version) {

    /** `SafetyChecklist`; `creatinine` i `egfr` pomijane, gdy brak (liczby bez zbednych zer). */
    public record Safety(
            PregnancyStatus pregnancy,
            boolean pacemakerOrImplant,
            boolean metalFragments,
            boolean contrastAllergy,
            BigDecimal creatinine,
            BigDecimal egfr,
            boolean claustrophobia,
            boolean confirmed) {
    }
}
