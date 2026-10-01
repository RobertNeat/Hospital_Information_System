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
}
