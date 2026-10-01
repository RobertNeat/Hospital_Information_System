package robert_neat.his_backend.lab;

import java.math.BigDecimal;
import java.time.Instant;

/** Wiersz projekcji JPQL dla trendu analitu (publiczny, bo Hibernate tworzy go przez konstruktor). */
public record TrendRow(
        Instant collectedAt,
        BigDecimal value,
        ObservationFlag flag,
        String analyteName,
        String unit,
        BigDecimal low,
        BigDecimal high) {
}
