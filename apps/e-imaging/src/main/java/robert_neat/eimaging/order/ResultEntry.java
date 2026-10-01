package robert_neat.eimaging.order;

import java.time.Instant;

/** Wynik badania (opis radiologa) wprowadzony w e-imaging; mutowalny tylko stan synchronizacji. */
public final class ResultEntry {

    private final String id;
    private final ResultStatus status;
    private final String radiologist;
    private final String findings;
    private final String conclusion;
    private final boolean critical;
    private final Instant performedAt;
    private final Instant reportedAt;

    private SyncState syncState;
    private String syncMessage;

    ResultEntry(String id, ResultStatus status, String radiologist, String findings, String conclusion,
            boolean critical, Instant performedAt, Instant reportedAt) {
        this.id = id;
        this.status = status;
        this.radiologist = radiologist;
        this.findings = findings;
        this.conclusion = conclusion;
        this.critical = critical;
        this.performedAt = performedAt;
        this.reportedAt = reportedAt;
        this.syncState = SyncState.PENDING;
    }

    public String getId() {
        return id;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public String getRadiologist() {
        return radiologist;
    }

    public String getFindings() {
        return findings;
    }

    public String getConclusion() {
        return conclusion;
    }

    public boolean isCritical() {
        return critical;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public Instant getReportedAt() {
        return reportedAt;
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
        ResultEntry c = new ResultEntry(id, status, radiologist, findings, conclusion, critical, performedAt,
                reportedAt);
        c.updateSync(syncState, syncMessage);
        return c;
    }
}
