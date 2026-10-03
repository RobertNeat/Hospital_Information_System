package robert_neat.his_backend.common.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operacje na outboxie wspolne dla e-receipt/e-laboratory/e-imaging. Kazda metoda dziala we wlasnej transakcji
 * `REQUIRES_NEW` - wywolujacy (listener AFTER_COMMIT lub scheduler) jest juz poza transakcja domenowa, a wpis
 * outboxa ma byc widoczny niezaleznie od wyniku reszty przetwarzania.
 * <p>
 * Backoff: stale (nie rosnace wykladniczo - prostota ponad wyrafinowanie, zgodnie z zakresem zadania) opoznienie
 * {@value #RETRY_DELAY_SECONDS}s miedzy probami, limit {@value #DEFAULT_MAX_ATTEMPTS} prob zanim wpis trafi w stan
 * `failed` (koncowy - dalej widoczny do analizy, ale pomijany przez scheduler).
 */
@Component
public class OutboxRetrySupport {

    static final int DEFAULT_MAX_ATTEMPTS = 8;
    static final long RETRY_DELAY_SECONDS = 30;

    private final OutboxRepository repository;

    OutboxRetrySupport(OutboxRepository repository) {
        this.repository = repository;
    }

    /** Rejestruje probe wysylki jako "do zrobienia" - wolana przed pierwsza (synchroniczna) proba, nie po niej. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(OutboxIntegration integration, OutboxOperation operation, UUID entityId) {
        repository.findByIntegrationAndOperationAndEntityId(integration, operation, entityId)
                .ifPresentOrElse(existing -> existing.reopen(Instant.now()),
                        () -> repository.save(new OutboxEntry(integration, operation, entityId,
                                DEFAULT_MAX_ATTEMPTS, Instant.now())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(OutboxIntegration integration, OutboxOperation operation, UUID entityId) {
        repository.findByIntegrationAndOperationAndEntityId(integration, operation, entityId)
                .ifPresent(OutboxEntry::markSucceeded);
    }

    /** Stan encji uczynil ponowienie bezprzedmiotowym (np. anulowana/usunieta w miedzyczasie) - bez kolejnych prob. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuperseded(OutboxIntegration integration, OutboxOperation operation, UUID entityId) {
        repository.findByIntegrationAndOperationAndEntityId(integration, operation, entityId)
                .ifPresent(OutboxEntry::markSuperseded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(OutboxIntegration integration, OutboxOperation operation, UUID entityId, String error) {
        repository.findByIntegrationAndOperationAndEntityId(integration, operation, entityId)
                .ifPresent(entry -> entry.recordFailure(error, Instant.now().plus(Duration.ofSeconds(RETRY_DELAY_SECONDS))));
    }
}
