package robert_neat.elaboratory.order;

import java.math.BigDecimal;

/**
 * Obserwacja wyniku: dokladnie jedna z wartosci (`numeric` albo `text`). `flag` (N, L, H, LL, HH, A) opcjonalna:
 * pusta oznacza, ze flage wyznaczy HIS z zakresu katalogu. Jednostka i zakres to definicja z zlecenia (informacyjnie).
 */
public record Observation(String analyteCode, String analyteName, String unit, BigDecimal low, BigDecimal high,
        BigDecimal numeric, String text, String flag) {
}
