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
 * (zlecajacy, lekarz prowadzacy, osoba przypisana do zadania). Push STOMP (`/user/queue/alerts` dla
 * `recipientIds`, `/topic/alerts/{wardId}`) nalezy wpiac przez `@TransactionalEventListener(AFTER_COMMIT)`.
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
