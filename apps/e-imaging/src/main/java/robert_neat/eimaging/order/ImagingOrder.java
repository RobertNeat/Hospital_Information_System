package robert_neat.eimaging.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Zlecenie badania obrazowego w e-imaging. Mutowalne sa tylko stan (`status`, `syncState`, `syncMessage`, `updatedAt`,
 * `startedAt`) i lista wynikow; zmiany serializuje {@link ImagingOrderService}, a odczyty zwracaja kopie.
 */
public final class ImagingOrder {

    private final ImagingOrderDraft draft;
    private final Instant receivedAt;
    private final List<ResultEntry> results = new ArrayList<>();

    private ImagingOrderStatus status;
    private SyncState syncState = SyncState.SYNCED;
    private String syncMessage;
    private Instant updatedAt;
    private Instant startedAt;

    ImagingOrder(ImagingOrderDraft draft, Instant receivedAt) {
        this.draft = draft;
        this.receivedAt = receivedAt;
        this.updatedAt = receivedAt;
        this.status = draft.status();
    }

    public String getHisOrderId() {
        return draft.hisOrderId();
    }

    public String getPatientRef() {
        return draft.patientRef();
    }

    public String getRequesterRef() {
        return draft.requesterRef();
    }

    public String getPriority() {
        return draft.priority();
    }

    public Instant getAuthoredOn() {
        return draft.authoredOn();
    }

    /** Termin badania (poczatek zarezerwowanego slotu); pusty, gdy zlecenie bez slotu. */
    public Instant getScheduledAt() {
        return draft.scheduledAt();
    }

    public String getSlotId() {
        return draft.slotId();
    }

    public String getExamCode() {
        return draft.examCode();
    }

    public String getExamName() {
        return draft.examName();
    }

    public String getModality() {
        return draft.modality();
    }

    public String getLaterality() {
        return draft.laterality();
    }

    public String getBodyRegion() {
        return draft.bodyRegion();
    }

    public boolean isContrast() {
        return draft.contrast();
    }

    public String getIndication() {
        return draft.indication();
    }

    public List<String> getNotes() {
        return draft.notes();
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public ImagingOrderStatus getStatus() {
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

    /** Moment rozpoczecia badania (stan `in_progress`); pusty, gdy badanie nie bylo w toku. */
    public Instant getStartedAt() {
        return startedAt;
    }

    public List<ResultEntry> getResults() {
        return results;
    }

    void update(ImagingOrderStatus status, SyncState syncState, String syncMessage, Instant at) {
        this.status = status;
        if (status == ImagingOrderStatus.IN_PROGRESS && startedAt == null) {
            this.startedAt = at;
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

    ImagingOrder copy() {
        ImagingOrder c = new ImagingOrder(draft, receivedAt);
        c.status = status;
        c.syncState = syncState;
        c.syncMessage = syncMessage;
        c.updatedAt = updatedAt;
        c.startedAt = startedAt;
        results.forEach(r -> c.results.add(r.copy()));
        return c;
    }
}
