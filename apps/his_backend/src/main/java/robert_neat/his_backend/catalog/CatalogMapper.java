package robert_neat.his_backend.catalog;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Reczne mapowanie encja -> DTO (bez MapStruct). Wywolywac w transakcji (kolekcje sa leniwe, open-in-view wylaczone).
 * Zbiory sa zamieniane na listy w stabilnej kolejnosci (kolejnosc deklaracji enuma albo alfabetycznie).
 */
public final class CatalogMapper {

    private static final Collator POLISH = Collator.getInstance(Locale.forLanguageTag("pl-PL"));

    private CatalogMapper() {
    }

    /** Porownywacz napisow wg polskiej kolacji (diakrytyki wg alfabetu). */
    static <T> Comparator<T> byText(Function<T, String> key) {
        return (a, b) -> POLISH.compare(key.apply(a), key.apply(b));
    }

    public static LabTestResponse toResponse(LabTest t) {
        List<LabAnalyteDefinitionResponse> analytes = t.getAnalytes().stream()
                .sorted(Comparator.comparing(LabAnalyteDefinition::getCode))
                .map(CatalogMapper::toResponse).toList();
        return new LabTestResponse(t.getCode(), t.getLoinc(), t.getName(), t.getCategory(),
                sortedEnums(t.getSpecimenTypes()), t.getDefaultSpecimen(), t.getTurnaroundHours(),
                t.isFastingRequired(), analytes);
    }

    static LabAnalyteDefinitionResponse toResponse(LabAnalyteDefinition a) {
        return new LabAnalyteDefinitionResponse(a.getCode(), a.getName(), a.getUnit(), plain(a.getLow()),
                plain(a.getHigh()));
    }

    public static LabPanelResponse toResponse(LabPanel p) {
        return new LabPanelResponse(p.getId(), p.getName(), p.getTestCodes().stream().sorted().toList());
    }

    public static ImagingExamResponse toResponse(ImagingExam e) {
        return new ImagingExamResponse(e.getCode(), e.getModality(), e.getName(), e.getBodyRegion(),
                e.isContrastPossible(), e.isRequiresLaterality(), e.getPreparation(), e.getDurationMinutes());
    }

    public static ScheduleSlotResponse toResponse(ScheduleSlot s) {
        return new ScheduleSlotResponse(s.getId(), s.getModality(), s.getStartAt(), s.getEndAt(), s.getRoom(),
                s.isAvailable());
    }

    public static DrugResponse toResponse(Drug d) {
        MaxDailyDose dose = d.getMaxDailyDose();
        DoseQuantityResponse maxDailyDose = dose == null || dose.getValue() == null ? null
                : new DoseQuantityResponse(plain(dose.getValue()), dose.getUnit());
        List<String> interactions = d.getInteractsWithAtc().isEmpty() ? null
                : d.getInteractsWithAtc().stream().sorted().toList();
        return new DrugResponse(d.getId(), d.getName(), d.getActiveSubstance(), d.getAtcCode(), d.getForm(),
                d.getStrength(), d.getPackageSize(), d.getPackageUnit(), sortedEnums(d.getRoutes()),
                d.getDefaultDoseUnit(), d.isRxOnly(), sortedEnums(d.getReimbursementOptions()), interactions,
                maxDailyDose);
    }

    public static VitalThresholdResponse toResponse(VitalThreshold v) {
        return new VitalThresholdResponse(v.getType(), v.getLabel(), v.getUnit(), plain(v.getLow()),
                plain(v.getHigh()), plain(v.getCriticalLow()), plain(v.getCriticalHigh()), plain(v.getMin()),
                plain(v.getMax()));
    }

    private static <E extends Enum<E>> List<E> sortedEnums(Set<E> values) {
        return values.stream().sorted().toList();
    }

    /** Liczba bez zbednych zer po przecinku (`90.0` -> `90`, `37.50` -> `37.5`), bez notacji wykladniczej. */
    public static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
