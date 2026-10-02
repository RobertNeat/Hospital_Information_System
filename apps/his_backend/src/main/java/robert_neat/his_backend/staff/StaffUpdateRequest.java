package robert_neat.his_backend.staff;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cialo `PUT /staff/{id}` (admin, `staff:write`). `employeeId` nie jest tu edytowalny (login konta,
 * `user_account.employee_id` jest `updatable=false`); do zmiany konta sluza `/activate` i `/lock`.
 */
public record StaffUpdateRequest(
        @NotBlank @Size(max = 50) String title,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull StaffRole role,
        @Size(max = 100) String specialization,
        @NotNull UUID wardId,
        @Size(max = 30) String phone,
        @Pattern(regexp = "^([0-9]{7})?$", message = "PWZ musi miec dokladnie 7 cyfr") String pwz,
        @Email @Size(max = 200) String email) {
}
