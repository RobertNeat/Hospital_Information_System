package robert_neat.his_backend.staff;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cialo `POST /wards`. */
public record WardCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 10) String shortName,
        @NotBlank @Size(max = 10) String floor,
        @NotNull @Min(0) Integer beds) {
}
