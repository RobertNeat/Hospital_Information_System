package robert_neat.his_backend.messaging;

import java.util.UUID;

/** Wynik zapytania o nieprzeczytane: watek + liczba wiadomosci (projekcja HQL). */
public record ThreadUnread(UUID threadId, Long count) {
}
