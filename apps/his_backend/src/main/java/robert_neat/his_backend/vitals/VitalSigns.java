package robert_neat.his_backend.vitals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.catalog.VitalType;

/**
 * Pojedynczy pomiar parametrow zyciowych (`vital_signs`). Niezmienny (korekta = nowy odczyt; brak `version` i audytu).
 * Plaski wiersz z kolumnami nullable, co najmniej jeden pomiar (CHECK w schemacie). Kolumny calkowite to `smallint`
 * (stad `@JdbcTypeCode`), temperatura `numeric(4,1)`. FK mapowane skalarnie. Tworzy go {@link VitalsService}.
 */
@Entity
@Immutable
@Table(name = "vital_signs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VitalSigns {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by_id", nullable = false, updatable = false)
    private UUID recordedById;

    @Column(name = "context", nullable = false, updatable = false, length = 15)
    private VitalContext context;

    @Column(name = "source", nullable = false, updatable = false, length = 10)
    private VitalSource source;

    @Column(name = "device_id", updatable = false, length = 50)
    private String deviceId;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "systolic", updatable = false)
    private Integer systolic;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "diastolic", updatable = false)
    private Integer diastolic;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "heart_rate", updatable = false)
    private Integer heartRate;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "spo2", updatable = false)
    private Integer spo2;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "respiratory_rate", updatable = false)
    private Integer respiratoryRate;

    @Column(name = "temperature", updatable = false, precision = 4, scale = 1)
    private BigDecimal temperature;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "pain_score", updatable = false)
    private Integer painScore;

    @Column(name = "notes", columnDefinition = "text", updatable = false)
    private String notes;

    /** Wartosc pomiaru danego typu albo `null`, gdy nie zostal wykonany. */
    public BigDecimal measurement(VitalType type) {
        return switch (type) {
            case SYSTOLIC -> toDecimal(systolic);
            case DIASTOLIC -> toDecimal(diastolic);
            case HEART_RATE -> toDecimal(heartRate);
            case TEMPERATURE -> temperature;
            case SPO2 -> toDecimal(spo2);
            case RESPIRATORY_RATE -> toDecimal(respiratoryRate);
        };
    }

    private static BigDecimal toDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    /** Nowy odczyt (wartosci juz zwalidowane przez serwis). */
    static VitalSigns record(UUID patientId, Instant recordedAt, UUID recordedById, VitalContext context,
            VitalSource source, String deviceId, UUID encounterId, Integer systolic, Integer diastolic,
            Integer heartRate, Integer spo2, Integer respiratoryRate, BigDecimal temperature, Integer painScore,
            String notes) {
        VitalSigns v = new VitalSigns();
        v.patientId = patientId;
        v.recordedAt = recordedAt;
        v.recordedById = recordedById;
        v.context = context;
        v.source = source;
        v.deviceId = deviceId;
        v.encounterId = encounterId;
        v.systolic = systolic;
        v.diastolic = diastolic;
        v.heartRate = heartRate;
        v.spo2 = spo2;
        v.respiratoryRate = respiratoryRate;
        v.temperature = temperature;
        v.painScore = painScore;
        v.notes = notes;
        return v;
    }
}
