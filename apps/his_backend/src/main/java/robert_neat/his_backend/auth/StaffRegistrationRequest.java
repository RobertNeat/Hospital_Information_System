package robert_neat.his_backend.auth;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import robert_neat.his_backend.staff.StaffRole;

/**
 * `StaffRegistrationRequest` z kontraktu. `employeeId` jak w walidatorze frontendu (4-20 znakow: litery, cyfry,
 * myslnik); haslo min. 8 znakow (BCrypt przetwarza maks. 72 bajty).
 */
public record StaffRegistrationRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull StaffRole role,
        @NotBlank @Size(max = 50) String title,
        @Size(max = 100) String specialization,
        @Pattern(regexp = "^([0-9]{7})?$", message = "PWZ musi miec dokladnie 7 cyfr") String pwz,
        @NotNull UUID wardId,
        @Size(max = 30) String phone,
        @Email @Size(max = 200) String email,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{4,20}$",
                message = "Identyfikator pracownika: 4-20 znakow (litery, cyfry, myslnik)") String employeeId,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
