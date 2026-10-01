package robert_neat.ereceipt.prescription;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Recepta w e-receipt. Mutowalny jest tylko stan (`status`, `syncState`, `syncMessage`, `updatedAt`);
 * zmiany serializuje {@link ReceiptService}, a odczyty zwracaja kopie.
 */
public final class Receipt {

    private final String erxKey;
    private final String hisPrescriptionId;
    private final String accessCode;
    private final String patientRef;
    private final String requesterRef;
    private final Instant authoredOn;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final String medication;
    private final List<String> dosages;
    private final String note;
    private final Instant receivedAt;

    private ReceiptStatus status = ReceiptStatus.ISSUED;
    private SyncState syncState = SyncState.SYNCED;
    private String syncMessage;
    private Instant updatedAt;

    public Receipt(String erxKey, String hisPrescriptionId, String accessCode, String patientRef,
            String requesterRef, Instant authoredOn, LocalDate validFrom, LocalDate validUntil, String medication,
            List<String> dosages, String note, Instant receivedAt) {
        this.erxKey = erxKey;
        this.hisPrescriptionId = hisPrescriptionId;
        this.accessCode = accessCode;
        this.patientRef = patientRef;
        this.requesterRef = requesterRef;
        this.authoredOn = authoredOn;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.medication = medication;
        this.dosages = List.copyOf(dosages);
        this.note = note;
        this.receivedAt = receivedAt;
        this.updatedAt = receivedAt;
    }

    public String getErxKey() {
        return erxKey;
    }

    public String getHisPrescriptionId() {
        return hisPrescriptionId;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public String getPatientRef() {
        return patientRef;
    }

    public String getRequesterRef() {
        return requesterRef;
    }

    public Instant getAuthoredOn() {
        return authoredOn;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public String getMedication() {
        return medication;
    }

    public List<String> getDosages() {
        return dosages;
    }

    public String getNote() {
        return note;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public ReceiptStatus getStatus() {
        return status;
    }

    public SyncState getSyncState() {
        return syncState;
    }

    public String getSyncMessage() {
        return syncMessage;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void update(ReceiptStatus status, SyncState syncState, String syncMessage, Instant at) {
        this.status = status;
        this.syncState = syncState;
        this.syncMessage = syncMessage;
        this.updatedAt = at;
    }

    void updateSync(SyncState syncState, String syncMessage, Instant at) {
        this.syncState = syncState;
        this.syncMessage = syncMessage;
        this.updatedAt = at;
    }

    Receipt copy() {
        Receipt c = new Receipt(erxKey, hisPrescriptionId, accessCode, patientRef, requesterRef, authoredOn,
                validFrom, validUntil, medication, dosages, note, receivedAt);
        c.update(status, syncState, syncMessage, updatedAt);
        return c;
    }
}
