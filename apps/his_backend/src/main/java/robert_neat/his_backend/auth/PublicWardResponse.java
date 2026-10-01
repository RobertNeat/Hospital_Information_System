package robert_neat.his_backend.auth;

import java.util.UUID;

/** Minimalny widok oddzialu dostepny publicznie (rejestracja konta). */
public record PublicWardResponse(UUID id, String name, String shortName) {
}
