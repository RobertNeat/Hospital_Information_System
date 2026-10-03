package robert_neat.his_backend.common.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Wiersz outboxa ponawiania wysylki do e-receipt/e-laboratory/e-imaging (`integration_outbox`). Celowo bez
 * payloadu FHIR: {@link robert_neat.his_backend.common.outbox.OutboxRetryable} odtwarza go z biezacego stanu
 * encji HIS (`entityId`) dopiero w chwili ponowienia - patrz uzasadnienie w changesetcie 014.
 */
@Entity
@Table(name = "integration_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "integration", nullable = false, updatable = false)
    private OutboxIntegration integration;

    @Column(name = "operation", nullable = false, updatable = false)
    private OutboxOperation operation;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "status", nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OutboxEntry(OutboxIntegration integration, OutboxOperation operation, UUID entityId, int maxAttempts,
            Instant now) {
        this.integration = integration;
        this.operation = operation;
        this.entityId = entityId;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = now;
        this.status = OutboxStatus.PENDING;
    }

    /** Resetuje wpis juz oznaczony jako koncowy (np. ponowna proba po bledzie, nowa proba wysylki). */
    public void reopen(Instant now) {
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.lastError = null;
        this.nextAttemptAt = now;
    }

    public void markSucceeded() {
        this.status = OutboxStatus.SUCCEEDED;
        this.lastError = null;
    }

    /** Encja przeszla stan, ktory czyni ponowienie bezprzedmiotowym (np. anulowana w miedzyczasie) - bez wysylki. */
    public void markSuperseded() {
        this.status = OutboxStatus.SUCCEEDED;
        this.lastError = "superseded";
    }

    /** Testy: wymusza natychmiastowa gotowosc do ponowienia, bez czekania na uplyw `nextAttemptAt`. */
    void forceDueNow() {
        this.nextAttemptAt = Instant.now().minusSeconds(1);
    }

    public void recordFailure(String error, Instant nextAttemptAt) {
        this.attempts++;
        this.lastError = error == null ? "" : error.length() > 2000 ? error.substring(0, 2000) : error;
        if (this.attempts >= this.maxAttempts) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING;
            this.nextAttemptAt = nextAttemptAt;
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
