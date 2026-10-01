package robert_neat.his_backend.lab;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.catalog.SpecimenType;

/**
 * Pozycja zlecenia (`lab_order_item`). `testName` to snapshot nazwy badania z chwili zlecenia; wlascicielem relacji
 * jest {@link LabOrder} (kolumna `order_id`). `specimenId` poza zakresem (brak encji materialu).
 */
@Entity
@Table(name = "lab_order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "test_code", nullable = false, updatable = false, length = 30)
    private String testCode;

    @Column(name = "test_name", nullable = false, updatable = false, length = 200)
    private String testName;

    @Column(name = "specimen_id")
    private UUID specimenId;

    @Column(name = "specimen_type", nullable = false, updatable = false, length = 10)
    private SpecimenType specimenType;

    static LabOrderItem of(String testCode, String testName, SpecimenType specimenType) {
        LabOrderItem item = new LabOrderItem();
        item.testCode = testCode;
        item.testName = testName;
        item.specimenType = specimenType;
        return item;
    }
}
