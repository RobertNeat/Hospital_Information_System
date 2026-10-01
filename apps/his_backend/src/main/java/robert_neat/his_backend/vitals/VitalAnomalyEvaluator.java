package robert_neat.his_backend.vitals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import robert_neat.his_backend.catalog.VitalThreshold;
import robert_neat.his_backend.catalog.VitalType;

/**
 * Wyznacza anomalie odczytu z progow `vital_threshold` (tabela jest zrodlem prawdy, nie stale frontendu).
 * Porownania sa scisle: wartosc rowna progowi jest jeszcze "w normie" / "jeszcze nie krytyczna".
 * <ol>
 *   <li>`value < criticalLow` / `value > criticalHigh` - `critical` (kierunek `low` / `high`);</li>
 *   <li>w przeciwnym razie `value < low` / `value > high` - `warning`;</li>
 *   <li>inaczej brak anomalii. Pomiary nieobecne i typy bez progu sa pomijane; `painScore` nie ma progow.</li>
 * </ol>
 * Kolejnosc wyniku = kolejnosc {@link VitalType}.
 */
final class VitalAnomalyEvaluator {

    private VitalAnomalyEvaluator() {
    }

    static List<VitalAnomaly> evaluate(VitalSigns vitals, Map<VitalType, VitalThreshold> thresholds) {
        List<VitalAnomaly> anomalies = new ArrayList<>();
        for (VitalType type : VitalType.values()) {
            BigDecimal value = vitals.measurement(type);
            VitalThreshold threshold = thresholds.get(type);
            if (value == null || threshold == null) {
                continue;
            }
            VitalAnomaly anomaly = evaluateOne(type, value, threshold, vitals);
            if (anomaly != null) {
                anomalies.add(anomaly);
            }
        }
        return anomalies;
    }

    private static VitalAnomaly evaluateOne(VitalType type, BigDecimal value, VitalThreshold t, VitalSigns vitals) {
        if (value.compareTo(t.getCriticalLow()) < 0) {
            return anomaly(type, value, AnomalySeverity.CRITICAL, AnomalyDirection.LOW, t, vitals);
        }
        if (value.compareTo(t.getCriticalHigh()) > 0) {
            return anomaly(type, value, AnomalySeverity.CRITICAL, AnomalyDirection.HIGH, t, vitals);
        }
        if (value.compareTo(t.getLow()) < 0) {
            return anomaly(type, value, AnomalySeverity.WARNING, AnomalyDirection.LOW, t, vitals);
        }
        if (value.compareTo(t.getHigh()) > 0) {
            return anomaly(type, value, AnomalySeverity.WARNING, AnomalyDirection.HIGH, t, vitals);
        }
        return null;
    }

    private static VitalAnomaly anomaly(VitalType type, BigDecimal value, AnomalySeverity severity,
            AnomalyDirection direction, VitalThreshold t, VitalSigns vitals) {
        BigDecimal plain = VitalsMapper.plain(value);
        String what = severity == AnomalySeverity.CRITICAL
                ? (direction == AnomalyDirection.LOW ? "wartość krytycznie niska" : "wartość krytycznie wysoka")
                : (direction == AnomalyDirection.LOW ? "wartość poniżej normy" : "wartość powyżej normy");
        String message = t.getLabel() + ": " + what + " (" + plain.toPlainString() + " " + t.getUnit() + ").";
        return new VitalAnomaly(type, plain, severity, direction, message, vitals.getRecordedAt());
    }
}
