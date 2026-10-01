package robert_neat.his_backend.staff;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Pracownik. FK do oddzialu mapowany skalarnie (`wardId`); tabela nie ma kolumn audytu ani `version`. */
@Entity
@Table(name = "staff_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffMember {

    @Id
    private UUID id;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "role", nullable = false, length = 30)
    private StaffRole role;

    @Column(name = "specialization", length = 100)
    private String specialization;

    @Column(name = "ward_id", nullable = false)
    private UUID wardId;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "pwz", length = 7)
    private String pwz;

    @Column(name = "employee_id", length = 30)
    private String employeeId;

    @Column(name = "email", length = 200)
    private String email;

    /** Nowy pracownik (identyfikator nadawany po stronie aplikacji). */
    public static StaffMember create(String title, String firstName, String lastName, StaffRole role,
            String specialization, UUID wardId, String phone, String pwz, String employeeId, String email) {
        StaffMember member = new StaffMember();
        member.id = UUID.randomUUID();
        member.title = title;
        member.firstName = firstName;
        member.lastName = lastName;
        member.role = role;
        member.specialization = specialization;
        member.wardId = wardId;
        member.phone = phone;
        member.pwz = pwz;
        member.employeeId = employeeId;
        member.email = email;
        return member;
    }
}
