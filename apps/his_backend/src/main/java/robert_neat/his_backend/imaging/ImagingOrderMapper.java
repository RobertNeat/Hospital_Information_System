package robert_neat.his_backend.imaging;

import java.math.BigDecimal;
import java.util.Comparator;

import robert_neat.his_backend.common.order.StatusChangeResponse;

/** Reczne mapowanie encja -> DTO; kolekcje leniwe, wiec w transakcji serwisu (open-in-view wylaczone). */
final class ImagingOrderMapper {

    /**
     * Rosnaco po `at`; wpisy z tym samym znacznikiem (np. `ordered` i `scheduled` przy zleceniu ze slotem) wg etapu
     * cyklu zycia (kolejnosc `OrderStatus`) - zapytanie samo nie rozstrzyga remisow deterministycznie.
     */
    private static final Comparator<ImagingOrderStatusChange> HISTORY_ORDER = Comparator
            .comparing(ImagingOrderStatusChange::getAt).thenComparing(c -> c.getStatus().ordinal());

    private ImagingOrderMapper() {
    }

    static ImagingOrderResponse toResponse(ImagingOrder o) {
        return new ImagingOrderResponse(o.getId(), o.getPatientId(), o.getEncounterId(), o.getExamCode(),
                o.getExamName(), o.getModality(), o.getBodyRegion(), o.getLaterality(), o.isContrast(),
                o.getClinicalIndication(), o.getClinicalQuestion(), o.getDiagnosisCode(), o.getUrgency(),
                toResponse(o.getSafety()), o.getSlotId(), o.getScheduledAt(), o.getOrderedById(), o.getOrderedAt(),
                o.getStatus(), o.getStatusHistory().stream().sorted(HISTORY_ORDER)
                        .map(ImagingOrderMapper::toResponse).toList(),
                o.getCreatedAt(), o.getCreatedById(), o.getUpdatedAt(), o.getUpdatedById(), o.getVersion());
    }

    private static ImagingOrderResponse.Safety toResponse(SafetyChecklist s) {
        return new ImagingOrderResponse.Safety(s.pregnancy(), s.pacemakerOrImplant(), s.metalFragments(),
                s.contrastAllergy(), plain(s.creatinine()), plain(s.egfr()), s.claustrophobia(), s.confirmed());
    }

    private static StatusChangeResponse toResponse(ImagingOrderStatusChange c) {
        return new StatusChangeResponse(c.getStatus(), c.getAt(), c.getById(), c.getNote());
    }

    /** Liczby w JSON bez zbednych zer i bez notacji wykladniczej (`0.9`, `78`). */
    static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
