package robert_neat.his_backend.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** `Address` z kontraktu (kolumny `address_*`); jednoczesnie DTO zadania i odpowiedzi. */
@Embeddable
public record Address(
        @NotBlank @Size(max = 200) @Column(name = "address_street", nullable = false, length = 200) String street,
        @NotBlank @Size(max = 20) @Column(name = "address_building_number", nullable = false, length = 20)
        String buildingNumber,
        @Size(max = 20) @Column(name = "address_apartment_number", length = 20) String apartmentNumber,
        @NotBlank @Size(max = 20) @Column(name = "address_postal_code", nullable = false, length = 20)
        String postalCode,
        @NotBlank @Size(max = 100) @Column(name = "address_city", nullable = false, length = 100) String city,
        @NotBlank @Size(max = 100) @Column(name = "address_country", nullable = false, length = 100)
        String country) {
}
