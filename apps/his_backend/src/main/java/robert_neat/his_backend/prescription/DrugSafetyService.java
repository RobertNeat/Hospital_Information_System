package robert_neat.his_backend.prescription;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.catalog.CatalogMapper;
import robert_neat.his_backend.catalog.Drug;
import robert_neat.his_backend.catalog.DrugRepository;
import robert_neat.his_backend.catalog.MaxDailyDose;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.text.TextFolding;
import robert_neat.his_backend.ehr.Allergy;
import robert_neat.his_backend.ehr.AllergyRepository;
import robert_neat.his_backend.ehr.AllergySeverity;
import robert_neat.his_backend.ehr.AllergyStatus;
import robert_neat.his_backend.patient.PatientRepository;

/**
 * Kontrola bezpieczenstwa leku (`POST /drug-safety-checks`). Ostrzezenia sa DORADCZE (nie blokuja wystawienia).
 * Dla kazdej sprawdzanej pozycji (w kolejnosci wejscia), w tej kolejnosci typow:
 * <ol>
 *   <li>`allergy` - aktywna alergia pacjenta: kod ATC alergii jest prefiksem ATC leku albo nazwa substancji alergii
 *       (bez wielkosci liter/diakrytykow) rowna substancji czynnej; severity `life_threatening`/`severe` -> `danger`,
 *       `mild`/`moderate` -> `warn`;</li>
 *   <li>`duplicate` (`warn`) - ta sama substancja czynna w aktywnych lekach pacjenta (zywe recepty) albo w
 *       wczesniejszej pozycji tej samej recepty (takze ten sam lek);</li>
 *   <li>`interaction` (`warn`) - `interactsWithAtc` (prefiks ATC) jednego z lekow pasuje do ATC drugiego, w obie
 *       strony: wzgledem aktywnych lekow pacjenta i wczesniejszych pozycji;</li>
 *   <li>`max_dose` (`danger`) - dawka dobowa pozycji (`dose` x liczba podan; PRN/`asNeeded`: `maxPerDay`) przekracza
 *       `maxDailyDose` leku. Tylko przy tej samej jednostce (bez przeliczen); bez sumowania pozycji.</li>
 * </ol>
 * 404: pacjent albo lek; 422: brak leku w zadaniu.
 */
@Service
@Transactional(readOnly = true)
public class DrugSafetyService {

    private final PatientRepository patients;
    private final DrugRepository drugs;
    private final AllergyRepository allergies;
    private final PrescriptionService prescriptions;

    DrugSafetyService(PatientRepository patients, DrugRepository drugs, AllergyRepository allergies,
            PrescriptionService prescriptions) {
        this.patients = patients;
        this.drugs = drugs;
        this.allergies = allergies;
        this.prescriptions = prescriptions;
    }

    public List<DrugSafetyWarningResponse> check(DrugSafetyCheckRequest request) {
        UUID patientId = request.patientId();
        List<DrugSafetyItemRequest> requested = new ArrayList<>();
        if (request.drugId() != null) {
            requested.add(new DrugSafetyItemRequest(request.drugId(), request.dosage()));
        }
        if (request.items() != null) {
            requested.addAll(request.items());
        }
        if (requested.isEmpty()) {
            throw new ValidationFailedException(List.of(new FieldError("drugId",
                    "Wymagany jest 'drugId' albo niepusta lista 'items'", "required")));
        }
        if (!patients.existsById(patientId)) {
            throw NotFoundException.of("Pacjent", patientId);
        }

        List<ActiveMedicationResponse> active = prescriptions.activeMedications(patientId);
        Set<UUID> drugIds = new HashSet<>();
        requested.forEach(i -> drugIds.add(i.drugId()));
        active.forEach(a -> drugIds.add(a.drugId()));
        Map<UUID, Drug> catalog = drugs.findAllById(drugIds).stream()
                .collect(Collectors.toMap(Drug::getId, Function.identity()));
        for (DrugSafetyItemRequest item : requested) {
            if (!catalog.containsKey(item.drugId())) {
                throw NotFoundException.of("Lek", item.drugId());
            }
        }

        List<Allergy> activeAllergies = allergies.findByPatientIdOrderByRecordedAtDescIdAsc(patientId).stream()
                .filter(a -> a.getStatus() == AllergyStatus.ACTIVE).toList();
        // aktywne leki pacjenta: jeden wpis na lek (ta sama pozycja moze byc na kilku receptach)
        Map<UUID, ActiveMedicationResponse> activeByDrug = new LinkedHashMap<>();
        active.forEach(a -> activeByDrug.putIfAbsent(a.drugId(), a));

        List<DrugSafetyWarningResponse> warnings = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            DrugSafetyItemRequest item = requested.get(i);
            Drug drug = catalog.get(item.drugId());
            checkAllergies(drug, activeAllergies, warnings);
            checkDuplicates(i, requested, catalog, activeByDrug.values(), warnings);
            checkInteractions(i, requested, catalog, activeByDrug, warnings);
            checkMaxDose(drug, item.dosage(), warnings);
        }
        return warnings;
    }

    private static void checkAllergies(Drug drug, List<Allergy> allergies, List<DrugSafetyWarningResponse> out) {
        for (Allergy allergy : allergies) {
            boolean atcMatch = allergy.getAtcCodes().stream()
                    .anyMatch(code -> !code.isBlank() && drug.getAtcCode().startsWith(code.trim()));
            boolean substanceMatch = sameSubstance(allergy.getSubstance(), drug.getActiveSubstance());
            if (atcMatch || substanceMatch) {
                DrugSafetySeverity severity = allergy.getSeverity() == AllergySeverity.LIFE_THREATENING
                        || allergy.getSeverity() == AllergySeverity.SEVERE ? DrugSafetySeverity.DANGER
                                : DrugSafetySeverity.WARN;
                out.add(new DrugSafetyWarningResponse(DrugSafetyWarningType.ALLERGY, severity, drug.getId(),
                        "Pacjent ma odnotowaną alergię na " + allergy.getSubstance() + " (" + allergy.getReaction()
                                + ") - lek " + drug.getName() + " (" + drug.getActiveSubstance() + ")."));
            }
        }
    }

    private static void checkDuplicates(int index, List<DrugSafetyItemRequest> requested, Map<UUID, Drug> catalog,
            java.util.Collection<ActiveMedicationResponse> active, List<DrugSafetyWarningResponse> out) {
        Drug drug = catalog.get(requested.get(index).drugId());
        for (ActiveMedicationResponse a : active) {
            if (sameSubstance(a.activeSubstance(), drug.getActiveSubstance())) {
                out.add(new DrugSafetyWarningResponse(DrugSafetyWarningType.DUPLICATE, DrugSafetySeverity.WARN,
                        drug.getId(), "Pacjent ma już aktywną receptę na lek zawierający "
                                + drug.getActiveSubstance() + " (" + a.drugName() + ")."));
                break;
            }
        }
        for (int j = 0; j < index; j++) {
            Drug other = catalog.get(requested.get(j).drugId());
            if (sameSubstance(other.getActiveSubstance(), drug.getActiveSubstance())) {
                out.add(new DrugSafetyWarningResponse(DrugSafetyWarningType.DUPLICATE, DrugSafetySeverity.WARN,
                        drug.getId(), "Na recepcie jest już lek zawierający " + drug.getActiveSubstance() + " ("
                                + other.getName() + ")."));
                break;
            }
        }
    }

    private static void checkInteractions(int index, List<DrugSafetyItemRequest> requested, Map<UUID, Drug> catalog,
            Map<UUID, ActiveMedicationResponse> activeByDrug, List<DrugSafetyWarningResponse> out) {
        Drug drug = catalog.get(requested.get(index).drugId());
        for (ActiveMedicationResponse a : activeByDrug.values()) {
            Drug other = catalog.get(a.drugId());
            if (other != null && interacts(drug, other)) {
                out.add(new DrugSafetyWarningResponse(DrugSafetyWarningType.INTERACTION, DrugSafetySeverity.WARN,
                        drug.getId(), "Możliwa interakcja leku " + drug.getName()
                                + " z aktualnie przyjmowanym lekiem " + a.drugName() + "."));
            }
        }
        for (int j = 0; j < index; j++) {
            Drug other = catalog.get(requested.get(j).drugId());
            if (interacts(drug, other)) {
                out.add(new DrugSafetyWarningResponse(DrugSafetyWarningType.INTERACTION, DrugSafetySeverity.WARN,
                        drug.getId(), "Możliwa interakcja leku " + drug.getName() + " z lekiem " + other.getName()
                                + " na tej samej recepcie."));
            }
        }
    }

    private static void checkMaxDose(Drug drug, DosageRequest dosage, List<DrugSafetyWarningResponse> out) {
        MaxDailyDose max = drug.getMaxDailyDose();
        if (dosage == null || max == null || max.getValue() == null
                || !TextFolding.fold(dosage.doseUnit().trim()).equals(TextFolding.fold(max.getUnit()))) {
            return;
        }
        BigDecimal daily = dailyDose(dosage);
        if (daily != null && daily.compareTo(max.getValue()) > 0) {
            out.add(new DrugSafetyWarningResponse(DrugSafetyWarningType.MAX_DOSE, DrugSafetySeverity.DANGER,
                    drug.getId(), "Dawka dobowa " + CatalogMapper.plain(daily.setScale(3, RoundingMode.HALF_UP)) + " "
                            + max.getUnit() + " przekracza maksymalną dawkę dobową leku "
                            + drug.getName() + " (" + CatalogMapper.plain(max.getValue()) + " " + max.getUnit()
                            + ")."));
        }
    }

    /**
     * Dawka dobowa jak w UI (`dosage-math.ts`): `dose` x podan na dobe z `frequency` (QW = 1/7); dla `asNeeded`/PRN
     * najgorszy przypadek `dose` x `maxPerDay` (bez `maxPerDay`: czestotliwosc, a dla PRN - pojedyncza dawka).
     */
    static BigDecimal dailyDose(DosageRequest d) {
        BigDecimal dose = d.dose();
        boolean prn = Boolean.TRUE.equals(d.asNeeded()) || d.frequency() == DoseFrequency.PRN;
        if (prn && d.maxPerDay() != null) {
            return dose.multiply(BigDecimal.valueOf(d.maxPerDay()));
        }
        return switch (d.frequency()) {
            case QD -> dose;
            case BID, Q12H -> dose.multiply(BigDecimal.valueOf(2));
            case TID, Q8H -> dose.multiply(BigDecimal.valueOf(3));
            case QID, Q6H -> dose.multiply(BigDecimal.valueOf(4));
            case Q4H -> dose.multiply(BigDecimal.valueOf(6));
            case QW -> dose.divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP);
            case PRN -> dose;
        };
    }

    /** Interakcja w obie strony: `interactsWithAtc` jednego leku (prefiks) pasuje do ATC drugiego. */
    private static boolean interacts(Drug a, Drug b) {
        return a.getInteractsWithAtc().stream().anyMatch(code -> b.getAtcCode().startsWith(code))
                || b.getInteractsWithAtc().stream().anyMatch(code -> a.getAtcCode().startsWith(code));
    }

    private static boolean sameSubstance(String a, String b) {
        return TextFolding.fold(a.trim()).equals(TextFolding.fold(b.trim()));
    }
}
