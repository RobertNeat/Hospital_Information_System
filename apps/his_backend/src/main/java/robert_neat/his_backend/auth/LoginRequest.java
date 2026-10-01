package robert_neat.his_backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** `LoginRequest` z kontraktu. Bez wzorca/min. dlugosci: konta proste (np. `user`) musza sie logowac. */
public record LoginRequest(
        @NotBlank @Size(max = 30) String employeeId,
        @NotBlank @Size(max = 200) String password) {
}
