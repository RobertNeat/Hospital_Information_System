package robert_neat.his_backend.lab;

import robert_neat.his_backend.common.order.StatusChangeResponse;

/** Reczne mapowanie encja -> DTO; kolekcje leniwe, wiec w transakcji serwisu (open-in-view wylaczone). */
final class LabOrderMapper {

    private LabOrderMapper() {
    }

    static LabOrderResponse toResponse(LabOrder o) {
        return new LabOrderResponse(o.getId(), o.getPatientId(), o.getEncounterId(), o.getOrderedById(),
                o.getOrderedAt(), o.getItems().stream().map(LabOrderMapper::toResponse).toList(), o.getUrgency(),
                o.isFasting(), o.getPlannedCollectionAt(), o.getDiagnosisCode(), o.getClinicalInfo(), o.getNotes(),
                o.getStatus(), o.getStatusHistory().stream().map(LabOrderMapper::toResponse).toList(),
                o.getCreatedAt(), o.getCreatedById(), o.getUpdatedAt(), o.getUpdatedById(), o.getVersion());
    }

    private static LabOrderItemResponse toResponse(LabOrderItem i) {
        return new LabOrderItemResponse(i.getId(), i.getTestCode(), i.getTestName(), i.getSpecimenId(),
                i.getSpecimenType());
    }

    private static StatusChangeResponse toResponse(LabOrderStatusChange c) {
        return new StatusChangeResponse(c.getStatus(), c.getAt(), c.getById(), c.getNote());
    }
}
