package robert_neat.elaboratory.order;

import java.time.Instant;
import java.util.List;

/** Wynik badania (pozycji zlecenia) wprowadzony w e-laboratory; mutowalny tylko stan synchronizacji. */
public final class ResultEntry {

    private final String id;
    private final String testCode;
    private final ResultStatus status;
    private final String performer;
    private final String comment;
    private final Instant collectedAt;
    private final Instant resultedAt;
    private final List<Observation> observations;

    private SyncState syncState;
    private String syncMessage;

    ResultEntry(String id, String testCode, ResultStatus status, String performer, String comment,
            Instant collectedAt, Instant resultedAt, List<Observation> observations) {
        this.id = id;
        this.testCode = testCode;
        this.status = status;
        this.performer = performer;
        this.comment = comment;
        this.collectedAt = collectedAt;
        this.resultedAt = resultedAt;
        this.observations = List.copyOf(observations);
        this.syncState = SyncState.PENDING;
    }

    public String getId() {
        return id;
    }

    public String getTestCode() {
        return testCode;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public String getPerformer() {
        return performer;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public Instant getResultedAt() {
        return resultedAt;
    }

    public List<Observation> getObservations() {
        return observations;
    }

    public SyncState getSyncState() {
        return syncState;
    }

    public String getSyncMessage() {
        return syncMessage;
    }

    void updateSync(SyncState state, String message) {
        this.syncState = state;
        this.syncMessage = message;
    }

    ResultEntry copy() {
        ResultEntry c = new ResultEntry(id, testCode, status, performer, comment, collectedAt, resultedAt,
                observations);
        c.updateSync(syncState, syncMessage);
        return c;
    }
}
