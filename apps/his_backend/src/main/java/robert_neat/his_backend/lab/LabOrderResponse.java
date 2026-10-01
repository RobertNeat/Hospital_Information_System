package robert_neat.his_backend.lab;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.order.StatusChangeResponse;
import robert_neat.his_backend.common.order.Urgency;
import robert_neat.his_backend.ehr.Coding;

/** LabOrder z kontraktu (pola opcjonalne pomijane, gdy brak; historia statusow rosnaco po `at`). */
public record LabOrderResponse(
        UUID id,
        UUID patientId,
        UUID encounterId,
        UUID orderedById,
        Instant orderedAt,
        List<LabOrderItemResponse> items,
        Urgency urgency,
        boolean fasting,
        Instant plannedCollectionAt,
        Coding diagnosisCode,
        String clinicalInfo,
        String notes,
        OrderStatus status,
        List<StatusChangeResponse> statusHistory,
        Instant createdAt,
        UUID createdById,
        Instant updatedAt,
        UUID updatedById,
        long version) {
}
