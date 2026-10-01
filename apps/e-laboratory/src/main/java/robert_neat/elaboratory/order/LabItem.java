package robert_neat.elaboratory.order;

import java.math.BigDecimal;
import java.util.List;

/** Pozycja zlecenia (badanie) z definicjami analitow przekazanymi przez HIS (pusta lista = brak definicji). */
public record LabItem(String testCode, String testName, String specimenType, List<Analyte> analytes) {

    public LabItem {
        analytes = List.copyOf(analytes);
    }

    /** Definicja analitu: kod, nazwa, jednostka i opcjonalny zakres referencyjny. */
    public record Analyte(String code, String name, String unit, BigDecimal low, BigDecimal high) {
    }
}
