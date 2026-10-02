package robert_neat.his_backend.staff;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ward")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ward {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "short_name", nullable = false, length = 10)
    private String shortName;

    @Column(name = "floor", nullable = false, length = 10)
    private String floor;

    @Column(name = "beds", nullable = false)
    private int beds;

    /** Nowy oddzial (identyfikator nadawany po stronie aplikacji). */
    public static Ward create(String name, String shortName, String floor, int beds) {
        Ward ward = new Ward();
        ward.id = java.util.UUID.randomUUID();
        ward.name = name;
        ward.shortName = shortName;
        ward.floor = floor;
        ward.beds = beds;
        return ward;
    }

    public void update(String name, String shortName, String floor, int beds) {
        this.name = name;
        this.shortName = shortName;
        this.floor = floor;
        this.beds = beds;
    }
}
