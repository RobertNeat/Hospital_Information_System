package robert_neat.his_backend.staff;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    List<UserAccount> findByStaffIdIn(Collection<UUID> staffIds);

    Optional<UserAccount> findByStaffId(UUID staffId);

    /** Z blokada zapisu: kolejne proby logowania na to samo konto licza sie sekwencyjnie. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.employeeId = :employeeId")
    Optional<UserAccount> findByEmployeeIdForUpdate(@Param("employeeId") String employeeId);

    boolean existsByEmployeeId(String employeeId);

    /**
     * Rzutowanie skalarne (bez hydratacji encji/cache poziomu 1): uzywane przy kazdym zadaniu do
     * porownania z claimem `tv` tokenu. `findById(...).getTokenVersion()` moglby zwrocic stara wartosc
     * z cache sesji po zewnetrznym (JDBC) bumpie w tej samej transakcji testowej.
     */
    @Query("select a.tokenVersion from UserAccount a where a.id = :accountId")
    Optional<Integer> findTokenVersionById(@Param("accountId") UUID accountId);

    /** Rzutowanie `id -> tokenVersion` dla wsadowego sweepu sesji STOMP. */
    interface IdAndTokenVersion {
        UUID getId();

        int getTokenVersion();
    }

    @Query("select a.id as id, a.tokenVersion as tokenVersion from UserAccount a where a.id in :accountIds")
    List<IdAndTokenVersion> findTokenVersionsByIdIn(@Param("accountIds") Collection<UUID> accountIds);
}
