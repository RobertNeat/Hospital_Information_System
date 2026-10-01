package robert_neat.his_backend.common.wire;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pomocnik wyszukiwania stalej enuma po wartosci na drucie. */
public final class WireEnums {

    private static final ClassValue<Map<String, Object>> LOOKUP = new ClassValue<>() {
        @Override
        protected Map<String, Object> computeValue(Class<?> type) {
            return Arrays.stream(type.getEnumConstants())
                    .collect(Collectors.toUnmodifiableMap(c -> ((WireEnum) c).wire(), Function.identity()));
        }
    };

    private WireEnums() {
    }

    /** Stala o danej wartosci na drucie albo {@link IllegalArgumentException} z lista dozwolonych wartosci. */
    public static <E extends Enum<E> & WireEnum> E fromWire(Class<E> type, String wire) {
        Object found = LOOKUP.get(type).get(wire);
        if (found == null) {
            throw new IllegalArgumentException(
                    "Niepoprawna wartosc '" + wire + "'; dozwolone: " + allowed(type));
        }
        return type.cast(found);
    }

    public static <E extends Enum<E> & WireEnum> String allowed(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(WireEnum::wire).collect(Collectors.joining(", "));
    }
}
