package robert_neat.his_backend.lab;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wewnetrzne polecenie zapisu wyniku (budowane z `DiagnosticReport` z `e-laboratory`; brak endpointu REST). Nazwa badania,
 * kategoria, nazwy i jednostki analitow oraz zakresy pochodza z katalogu (snapshot). `orderId`/`orderItemId`
 * opcjonalne (wynik zewnetrzny); samo `orderItemId` wystarcza (zlecenie wynika z pozycji), samo `orderId` - pozycja
 * wskazywana kodem badania. `performerName` pusty = nazwa zalogowanego pracownika.
 */
public record RecordLabResultCommand(
        UUID patientId,
        UUID orderId,
        UUID orderItemId,
        String testCode,
        Instant collectedAt,
        Instant resultedAt,
        ResultStatus status,
        String performerName,
        String comment,
        List<ObservationInput> observations) {

    /**
     * Obserwacja: dokladnie jedna z wartosci (`numericValue` albo `textValue`). `flag` opcjonalna: jawnie ustawiona
     * (np. `LL`/`HH`) ma pierwszenstwo; inaczej dla liczby wyznaczana z zakresu katalogu (N/L/H), dla tekstu `N`.
     */
    public record ObservationInput(String analyteCode, BigDecimal numericValue, String textValue,
            ObservationFlag flag) {

        public static ObservationInput numeric(String analyteCode, BigDecimal value) {
            return new ObservationInput(analyteCode, value, null, null);
        }

        public static ObservationInput numeric(String analyteCode, BigDecimal value, ObservationFlag flag) {
            return new ObservationInput(analyteCode, value, null, flag);
        }

        public static ObservationInput text(String analyteCode, String value, ObservationFlag flag) {
            return new ObservationInput(analyteCode, null, value, flag);
        }
    }
}
