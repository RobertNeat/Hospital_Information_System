package robert_neat.his_backend.prescription;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.catalog.Drug;
import robert_neat.his_backend.catalog.DrugForm;
import robert_neat.his_backend.catalog.ReimbursementLevel;

/**
 * Pozycja recepty (`prescription_item`). Nazwa, substancja, moc i postac to SNAPSHOT z katalogu z chwili
 * wystawienia (`@snapshot` w kontrakcie) - pozniejsze zmiany katalogu nie zmieniaja wystawionej recepty, dlatego
 * kolumny sa `updatable = false`. Wlascicielem relacji jest {@link Prescription} (kolumna `prescription_id`).
 */
@Entity
@Table(name = "prescription_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "drug_id", nullable = false, updatable = false)
    private UUID drugId;

    @Column(name = "drug_name", nullable = false, updatable = false, length = 200)
    private String drugName;

    @Column(name = "active_substance", nullable = false, updatable = false, length = 200)
    private String activeSubstance;

    @Column(name = "strength", nullable = false, updatable = false, length = 50)
    private String strength;

    @Column(name = "form", nullable = false, updatable = false, length = 15)
    private DrugForm form;

    @Embedded
    private DosageInstruction dosage;

    @Column(name = "quantity_packages", nullable = false)
    private int quantityPackages;

    @Column(name = "reimbursement", nullable = false, length = 10)
    private ReimbursementLevel reimbursement;

    @Column(name = "substitution_allowed", nullable = false)
    private boolean substitutionAllowed;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "prescription_item_time_of_day", joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "time_of_day", nullable = false, length = 10)
    @BatchSize(size = 100)
    private Set<TimeOfDay> timesOfDay = new HashSet<>();

    /** Pozycja ze snapshotem z `drug` (nazwa handlowa, substancja czynna, moc, postac). */
    static PrescriptionItem of(Drug drug, DosageInstruction dosage, Set<TimeOfDay> timesOfDay, int quantityPackages,
            ReimbursementLevel reimbursement, boolean substitutionAllowed) {
        PrescriptionItem item = new PrescriptionItem();
        item.drugId = drug.getId();
        item.drugName = drug.getName();
        item.activeSubstance = drug.getActiveSubstance();
        item.strength = drug.getStrength();
        item.form = drug.getForm();
        item.dosage = dosage;
        item.timesOfDay.addAll(timesOfDay);
        item.quantityPackages = quantityPackages;
        item.reimbursement = reimbursement;
        item.substitutionAllowed = substitutionAllowed;
        return item;
    }
}
