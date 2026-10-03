package robert_neat.his_backend.imaging;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import robert_neat.his_backend.common.order.Urgency;
import robert_neat.his_backend.ehr.Coding;

/**
 * ImagingOrderCreateRequest z kontraktu. Aktor zawsze z sesji, wiec pole orderedById nie wystepuje w zadaniu;
 * podobnie pominiete sa pola snapshotu (`examName`, `modality`, `bodyRegion` - z katalogu) i `scheduledAt`
 * (wynika ze slotu); `patientId` (opcjonalny) musi byc zgodny ze sciezka (422). `laterality` pominiete = `na`
 * (wymagane inne dla badan z `requiresLaterality`).
 */
public record ImagingOrderCreateRequest(
        UUID patientId,
        UUID encounterId,
        @NotBlank String examCode,
        Laterality laterality,
        @NotNull Boolean contrast,
        @NotBlank String clinicalIndication,
        String clinicalQuestion,
        @Valid Coding diagnosisCode,
        @NotNull Urgency urgency,
        @NotNull @Valid Safety safety,
        UUID slotId) {

    /** `SafetyChecklist` w zadaniu; `creatinine` (mg/dl, 2 miejsca) i `egfr` (1 miejsce) opcjonalne. */
    public record Safety(
            @NotNull PregnancyStatus pregnancy,
            @NotNull Boolean pacemakerOrImplant,
            @NotNull Boolean metalFragments,
            @NotNull Boolean contrastAllergy,
            @PositiveOrZero @Digits(integer = 4, fraction = 2) BigDecimal creatinine,
            @PositiveOrZero @Digits(integer = 5, fraction = 1) BigDecimal egfr,
            @NotNull Boolean claustrophobia,
            @NotNull Boolean confirmed) {
    }
}
