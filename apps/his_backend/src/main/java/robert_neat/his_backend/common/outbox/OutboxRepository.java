package robert_neat.his_backend.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    Optional<OutboxEntry> findByIntegrationAndOperationAndEntityId(OutboxIntegration integration,
            OutboxOperation operation, UUID entityId);

    /**
     * Wiersze gotowe do ponowienia (zaplanowany termin minal), najstarszy termin pierwszy. `Limit` ogranicza
     * jedno przejscie schedulera, zeby pojedyncza seria wolnych/zawieszonych wywolan HTTP nie zdominowala watku
     * schedulera (Spring Boot domyslnie uzywa jednowatkowej puli `@Scheduled`, wspoldzielonej ze `StompTokenVersionSweeper`).
     */
    @Query("select o from OutboxEntry o where o.status = robert_neat.his_backend.common.outbox.OutboxStatus.PENDING "
            + "and o.nextAttemptAt <= :now order by o.nextAttemptAt asc")
    List<OutboxEntry> findDue(@Param("now") Instant now, Limit limit);

    /**
     * "Zajmuje" wiersz przed proba wysylki przesuwajac `next_attempt_at` o `lease` - gdyby proces zabil watek w
     * trakcie wywolania HTTP, wiersz nie zostanie podjety ponownie natychmiast przez kolejny przebieg schedulera.
     * Warunek `status = PENDING and next_attempt_at <= :now` dziala jak optymistyczna rezerwacja: 0 zaktualizowanych
     * wierszy oznacza, ze ktos juz przejal wpis (lub zdazyl zmienic stan) - wywolujacy wtedy pomija ten wiersz.
     */
    @Modifying
    @Query("update OutboxEntry o set o.nextAttemptAt = :lease where o.id = :id "
            + "and o.status = robert_neat.his_backend.common.outbox.OutboxStatus.PENDING and o.nextAttemptAt <= :now")
    int claim(@Param("id") UUID id, @Param("now") Instant now, @Param("lease") Instant lease);
}
