package robert_neat.elaboratory.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Zlecenie laboratoryjne w e-laboratory. Mutowalne sa tylko stan (`status`, `syncState`, `syncMessage`, `updatedAt`,
 * `collectedAt`) i lista wynikow; zmiany serializuje {@link LabOrderService}, a odczyty zwracaja kopie.
 */
public final class LabOrder {

    private final String hisOrderId;
    private final String patientRef;
    private final String requesterRef;
    private final String priority;
    private final Instant authoredOn;
    private final Instant plannedCollectionAt;
    private final boolean fasting;
    private final List<String> notes;
    private final List<LabItem> items;
    private final Instant receivedAt;
    private final List<ResultEntry> results = new ArrayList<>();

    private LabOrderStatus status = LabOrderStatus.ORDERED;
    private SyncState syncState = SyncState.SYNCED;
    private String syncMessage;
    private Instant updatedAt;
    private Instant collectedAt;

    LabOrder(LabOrderDraft d, Instant receivedAt) {
        this.hisOrderId = d.hisOrderId();
        this.patientRef = d.patientRef();
        this.requesterRef = d.requesterRef();
        this.priority = d.priority();
        this.authoredOn = d.authoredOn();
        this.plannedCollectionAt = d.plannedCollectionAt();
        this.fasting = d.fasting();
        this.notes = d.notes();
        this.items = d.items();
        this.receivedAt = receivedAt;
        this.updatedAt = receivedAt;
    }

    public String getHisOrderId() {
        return hisOrderId;
    }

    public String getPatientRef() {
        return patientRef;
    }

    public String getRequesterRef() {
        return requesterRef;
    }

    public String getPriority() {
        return priority;
    }

    public Instant getAuthoredOn() {
        return authoredOn;
    }

    public Instant getPlannedCollectionAt() {
        return plannedCollectionAt;
    }

    public boolean isFasting() {
        return fasting;
    }

    public List<String> getNotes() {
        return notes;
    }

    public List<LabItem> getItems() {
        return items;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public LabOrderStatus getStatus() {
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

    /** Moment pobrania materialu (ustawiany przy stanie `specimen_collected`); pusty, gdy nie zarejestrowano. */
    public Instant getCollectedAt() {
        return collectedAt;
    }

    public List<ResultEntry> getResults() {
        return results;
    }

    void update(LabOrderStatus status, SyncState syncState, String syncMessage, Instant at) {
        this.status = status;
        if (status == LabOrderStatus.SPECIMEN_COLLECTED && collectedAt == null) {
            this.collectedAt = at;
        }
        this.syncState = syncState;
        this.syncMessage = syncMessage;
        this.updatedAt = at;
    }

    void updateSync(SyncState syncState, String syncMessage, Instant at) {
        this.syncState = syncState;
        this.syncMessage = syncMessage;
        this.updatedAt = at;
    }

    void addResult(ResultEntry entry, Instant at) {
        results.add(entry);
        this.updatedAt = at;
    }

    LabOrder copy() {
        LabOrder c = new LabOrder(new LabOrderDraft(hisOrderId, patientRef, requesterRef, priority, authoredOn,
                plannedCollectionAt, fasting, notes, items), receivedAt);
        c.status = status;
        c.syncState = syncState;
        c.syncMessage = syncMessage;
        c.updatedAt = updatedAt;
        c.collectedAt = collectedAt;
        results.forEach(r -> c.results.add(r.copy()));
        return c;
    }
}
