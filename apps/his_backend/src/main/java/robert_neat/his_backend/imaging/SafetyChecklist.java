package robert_neat.his_backend.imaging;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * `SafetyChecklist` z kontraktu - lista kontrolna bezpieczenstwa osadzona w zleceniu (kolumny `safety_*`).
 * `creatinine` i `egfr` opcjonalne (null = brak oznaczenia).
 */
@Embeddable
public record SafetyChecklist(
        @Column(name = "safety_pregnancy", nullable = false, length = 10) PregnancyStatus pregnancy,
        @Column(name = "safety_pacemaker_or_implant", nullable = false) boolean pacemakerOrImplant,
        @Column(name = "safety_metal_fragments", nullable = false) boolean metalFragments,
        @Column(name = "safety_contrast_allergy", nullable = false) boolean contrastAllergy,
        @Column(name = "safety_creatinine", precision = 6, scale = 2) BigDecimal creatinine,
        @Column(name = "safety_egfr", precision = 6, scale = 1) BigDecimal egfr,
        @Column(name = "safety_claustrophobia", nullable = false) boolean claustrophobia,
        @Column(name = "safety_confirmed", nullable = false) boolean confirmed) {
}
