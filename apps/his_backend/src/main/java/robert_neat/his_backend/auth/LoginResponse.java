package robert_neat.his_backend.auth;

import java.time.Instant;

/** `LoginResponse` z kontraktu: token Bearer (stateless), biezacy uzytkownik i moment wygasniecia tokenu. */
public record LoginResponse(String accessToken, CurrentUserResponse user, Instant expiresAt) {
}
