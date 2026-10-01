package robert_neat.his_backend.catalog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Slot grafiku pracowni obrazowej (`schedule_slot`). Celowo bez `@Immutable`: kolumna `available` zmienia sie przy
 * rezerwacji/zwolnieniu slotu przez zlecenie obrazowe ({@link #reserve}, {@link #release}); poza tym modul `catalog`
 * tylko czyta.
 */
@Entity
@Table(name = "schedule_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleSlot {

    @Id
    private UUID id;

    @Column(name = "modality", nullable = false, length = 15)
    private ImagingModality modality;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "room", nullable = false, length = 50)
    private String room;

    @Column(name = "available", nullable = false)
    private boolean available;

    /** Rezerwuje slot (`available = false`); wywolujacy sprawdza wczesniej dostepnosc. */
    public void reserve() {
        this.available = false;
    }

    /** Zwalnia slot (`available = true`), np. po anulowaniu zlecenia. */
    public void release() {
        this.available = true;
    }
}
