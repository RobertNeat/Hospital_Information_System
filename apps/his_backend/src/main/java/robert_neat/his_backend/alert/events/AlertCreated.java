package robert_neat.his_backend.alert.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.alert.AlertSeverity;
import robert_neat.his_backend.alert.AlertTarget;
import robert_neat.his_backend.alert.AlertType;

/**
 * Zdarzenie domenowe: utworzono alert (publikowane w transakcji zrodlowej). Nosi dane rozgloszeniowe, ktorych
 * nie przechowuje model alertu: `wardId` (oddzial aktywnego przyjecia pacjenta albo `null`) i `recipientIds`
 * (zlecajacy, lekarz prowadzacy, osoba przypisana do zadania). Konsument: `realtime/RealtimePublisher`
 * (`@TransactionalEventListener(AFTER_COMMIT)`) - push STOMP `/user/queue/alerts` dla `recipientIds` i
 * `/topic/alerts/{wardId}`.
 */
public record AlertCreated(
        UUID alertId,
        AlertType type,
        AlertSeverity severity,
        UUID patientId,
        String message,
        Instant createdAt,
        AlertTarget target,
        UUID wardId,
        List<UUID> recipientIds) {
}
