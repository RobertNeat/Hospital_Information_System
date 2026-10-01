package robert_neat.his_backend.patient;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Przyjecie (ADT). Wlasciciel relacji 1:1 z {@link Encounter} (`encounter_id`). FK mapowane skalarnie.
 * Tabela nie ma kolumn audytu, ma tylko `version`. Co najwyzej jedno przyjecie `active` na pacjenta
 * (czesciowy indeks unikalny w schemacie).
 */
@Entity
@Table(name = "admission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "status", nullable = false, length = 10)
    private AdmissionRecordStatus status;

    @Column(name = "admission_type", nullable = false, length = 10)
    private AdmissionType admissionType;

    @Column(name = "admitted_at", nullable = false)
    private Instant admittedAt;

    @Column(name = "ward_id", nullable = false)
    private UUID wardId;

    @Column(name = "room", length = 20)
    private String room;

    @Column(name = "bed", length = 20)
    private String bed;

    @Column(name = "attending_physician_id", nullable = false)
    private UUID attendingPhysicianId;

    @Column(name = "triage_level", length = 10)
    private TriageLevel triageLevel;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "referral_number", length = 50)
    private String referralNumber;

    @Column(name = "discharged_at")
    private Instant dischargedAt;

    @Column(name = "discharge_disposition", length = 20)
    private DischargeDisposition dischargeDisposition;

    @Column(name = "discharge_summary_note_id")
    private UUID dischargeSummaryNoteId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static Admission open(UUID patientId, UUID encounterId, AdmitPatientRequest r) {
        Admission a = new Admission();
        a.patientId = patientId;
        a.encounterId = encounterId;
        a.status = AdmissionRecordStatus.ACTIVE;
        a.admissionType = r.admissionType();
        a.admittedAt = r.admittedAt();
        a.wardId = r.wardId();
        a.room = r.room();
        a.bed = r.bed();
        a.attendingPhysicianId = r.attendingPhysicianId();
        a.triageLevel = r.triageLevel();
        a.reason = r.reason();
        a.referralNumber = r.referralNumber();
        return a;
    }

    public void discharge(Instant at, DischargeDisposition disposition, UUID summaryNoteId) {
        this.status = AdmissionRecordStatus.DISCHARGED;
        this.dischargedAt = at;
        this.dischargeDisposition = disposition;
        this.dischargeSummaryNoteId = summaryNoteId;
    }
}
