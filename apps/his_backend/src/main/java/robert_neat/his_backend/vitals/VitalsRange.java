package robert_neat.his_backend.vitals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import robert_neat.his_backend.common.wire.WireEnum;

/** `VitalsRange` (parametr `range` zapytania): okno czasu liczone wstecz od teraz. */
public enum VitalsRange implements WireEnum {
    H24("24h"),
    D7("7d"),
    D30("30d"),
    ALL("all");

    private final String wire;

    VitalsRange(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    /** Poczatek okna (wlacznie) wzgledem `now` albo `null` dla `all`. */
    public Instant from(Instant now) {
        return switch (this) {
            case H24 -> now.minus(24, ChronoUnit.HOURS);
            case D7 -> now.minus(7, ChronoUnit.DAYS);
            case D30 -> now.minus(30, ChronoUnit.DAYS);
            case ALL -> null;
        };
    }
}
