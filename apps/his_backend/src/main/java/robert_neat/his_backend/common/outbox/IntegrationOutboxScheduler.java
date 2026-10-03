package robert_neat.his_backend.common.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ponawia nieudane wysylki do e-receipt/e-laboratory/e-imaging (`integration_outbox`, status `pending`, termin
 * minal). Interwal staly {@value #INTERVAL_MS}ms (`@Scheduled` z `fixedDelay` - kolejny przebieg startuje dopiero
 * po zakonczeniu poprzedniego, bez nakladania sie przebiegow), wspoldzieli pule schedulera Springa z
 * `StompTokenVersionSweeper` (`@EnableScheduling` juz wlaczony w `WebSocketConfig` - tu nie dodajemy drugiego).
 * <p>
 * Kazdy wiersz jest "zajmowany" przed wyslaniem ({@link OutboxRepository#claim}, przesuniecie `next_attempt_at`
 * o {@value #LEASE_SECONDS}s) - gdyby proces padl w trakcie wywolania HTTP, wiersz nie zostanie podjety ponownie
 * od razu przez kolejny przebieg, tylko po uplywie dzierzawy. Rozmiar partii ograniczony
 * ({@value #BATCH_SIZE}), zeby seria wolnych/zawieszonych wywolan HTTP nie zablokowala watku schedulera na dlugo.
 */
@Component
public class IntegrationOutboxScheduler {

    static final long INTERVAL_MS = 30_000;
    static final long LEASE_SECONDS = 60;
    static final int BATCH_SIZE = 20;

    private static final Logger log = LoggerFactory.getLogger(IntegrationOutboxScheduler.class);

    private final OutboxRepository repository;
    private final Map<OutboxIntegration, OutboxRetryable> retryables;
    private final TransactionTemplate tx;

    IntegrationOutboxScheduler(OutboxRepository repository, List<OutboxRetryable> retryables,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.retryables = new EnumMap<>(OutboxIntegration.class);
        retryables.forEach(r -> this.retryables.put(r.integration(), r));
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelay = INTERVAL_MS)
    void retryDue() {
        Instant now = Instant.now();
        List<OutboxEntry> due = tx.execute(s -> repository.findDue(now, Limit.of(BATCH_SIZE)));
        if (due == null || due.isEmpty()) {
            return;
        }
        for (OutboxEntry entry : due) {
            retryOne(entry.getId(), entry.getIntegration(), entry.getOperation(), entry.getEntityId());
        }
    }

    private void retryOne(UUID outboxId, OutboxIntegration integration, OutboxOperation operation, UUID entityId) {
        Instant now = Instant.now();
        Boolean claimed = tx.execute(s -> repository.claim(outboxId, now, now.plus(Duration.ofSeconds(LEASE_SECONDS))) == 1);
        if (claimed == null || !claimed) {
            // Juz zajety przez rownolegly przebieg (w praktyce nie wystapi - pula schedulera jest jednowatkowa -
            // ale sprawdzenie jest tanie i chroni przed zmiana zalozenia w przyszlosci) albo stan zmienil sie
            // w miedzyczasie (np. ktos ustawil retry recznie) - pomin bez bledu.
            return;
        }
        OutboxRetryable retryable = retryables.get(integration);
        if (retryable == null) {
            log.warn("Brak obslugi retry dla integracji {} (wpis outboxa {})", integration, outboxId);
            return;
        }
        try {
            retryable.retry(operation, entityId);
        } catch (RuntimeException e) {
            // Awaryjny odlow: implementacje same zapisuja wynik (markSucceeded/recordFailure/markSuperseded) -
            // dotarcie tutaj oznacza blad poza tym kontraktem (np. NPE), ktorego nie wolno zgubic w cichym retry.
            log.warn("Nieoczekiwany blad podczas ponawiania wpisu outboxa {} ({} {}): {}", outboxId, integration,
                    operation, e.getMessage(), e);
        }
    }
}
