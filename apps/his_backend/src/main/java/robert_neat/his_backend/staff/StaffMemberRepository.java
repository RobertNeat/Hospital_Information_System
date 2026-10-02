package robert_neat.his_backend.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {

    @Query("""
            select s from StaffMember s
            where (:role is null or s.role = :role)
              and (:wardId is null or s.wardId = :wardId)
            """)
    List<StaffMember> search(@Param("role") StaffRole role, @Param("wardId") UUID wardId, Sort sort);

    boolean existsByEmployeeId(String employeeId);

    boolean existsByPwz(String pwz);

    boolean existsByPwzAndIdNot(String pwz, UUID id);

    @Query("select count(s) > 0 from StaffMember s where lower(s.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(s) > 0 from StaffMember s where lower(s.email) = lower(:email) and s.id <> :id")
    boolean existsByEmailIgnoreCaseAndIdNot(@Param("email") String email, @Param("id") UUID id);
}
