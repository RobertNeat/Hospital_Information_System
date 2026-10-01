package robert_neat.his_backend.prescription;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.catalog.AdministrationRoute;

/**
 * Dawkowanie pozycji recepty (kolumny `dosage_*` w `prescription_item`). Pory dnia (`timesOfDay`) to osobna tabela
 * `prescription_item_time_of_day`, wiec sa kolekcja pozycji ({@link PrescriptionItem#getTimesOfDay()}).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DosageInstruction {

    @Column(name = "dosage_dose", nullable = false, precision = 10, scale = 3)
    private BigDecimal dose;

    @Column(name = "dosage_dose_unit", nullable = false, length = 30)
    private String doseUnit;

    @Column(name = "dosage_route", nullable = false, length = 15)
    private AdministrationRoute route;

    @Column(name = "dosage_frequency", nullable = false, length = 5)
    private DoseFrequency frequency;

    @Column(name = "dosage_duration_days", nullable = false)
    private int durationDays;

    @Column(name = "dosage_as_needed", nullable = false)
    private boolean asNeeded;

    @Column(name = "dosage_max_per_day")
    private Integer maxPerDay;

    @Column(name = "dosage_instructions", columnDefinition = "text")
    private String instructions;

    static DosageInstruction of(BigDecimal dose, String doseUnit, AdministrationRoute route, DoseFrequency frequency,
            int durationDays, boolean asNeeded, Integer maxPerDay, String instructions) {
        DosageInstruction d = new DosageInstruction();
        d.dose = dose;
        d.doseUnit = doseUnit;
        d.route = route;
        d.frequency = frequency;
        d.durationDays = durationDays;
        d.asNeeded = asNeeded;
        d.maxPerDay = maxPerDay;
        d.instructions = instructions;
        return d;
    }
}
