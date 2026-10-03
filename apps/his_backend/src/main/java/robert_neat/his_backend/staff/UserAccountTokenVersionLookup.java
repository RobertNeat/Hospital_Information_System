package robert_neat.his_backend.staff;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import robert_neat.his_backend.security.TokenVersionLookup;

/** {@link TokenVersionLookup} wspierany rzutowaniem skalarnym repozytorium (bez hydratacji encji). */
@Component
class UserAccountTokenVersionLookup implements TokenVersionLookup {

    private final UserAccountRepository accounts;

    UserAccountTokenVersionLookup(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<Integer> currentVersion(UUID accountId) {
        return accounts.findTokenVersionById(accountId);
    }

    @Override
    public Map<UUID, Integer> currentVersions(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accounts.findTokenVersionsByIdIn(accountIds).stream()
                .collect(Collectors.toMap(UserAccountRepository.IdAndTokenVersion::getId,
                        UserAccountRepository.IdAndTokenVersion::getTokenVersion));
    }
}
