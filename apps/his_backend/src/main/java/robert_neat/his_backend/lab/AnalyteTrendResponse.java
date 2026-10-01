package robert_neat.his_backend.lab;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * `AnalyteTrend` z kontraktu. Naglowek (`analyteName`, `unit`, `low`, `high`) z najnowszego punktu; bez punktow -
 * z definicji w katalogu (albo `analyteName` = kod, `unit` = pusty string). Punkty rosnaco po dacie pobrania.
 */
public record AnalyteTrendResponse(
        String analyteCode,
        String analyteName,
        String unit,
        BigDecimal low,
        BigDecimal high,
        List<Point> points) {

    /** `TrendPoint`: `at` = `collectedAt` wyniku. */
    public record Point(Instant at, BigDecimal value, ObservationFlag flag) {
    }
}
