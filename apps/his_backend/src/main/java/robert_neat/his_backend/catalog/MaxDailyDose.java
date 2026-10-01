package robert_neat.his_backend.catalog;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Maksymalna dawka dobowa leku (`max_daily_dose_value` + `max_daily_dose_unit`; obie kolumny albo zadna). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaxDailyDose {

    @Column(name = "max_daily_dose_value", precision = 12, scale = 3)
    private BigDecimal value;

    @Column(name = "max_daily_dose_unit", length = 30)
    private String unit;
}
