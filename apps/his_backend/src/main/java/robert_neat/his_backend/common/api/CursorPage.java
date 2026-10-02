package robert_neat.his_backend.common.api;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Strona wynikow dla paginacji kursorowej (`before=<timestamp>`, malejaco po czasie). `nextBefore` to
 * znacznik czasu najstarszego elementu na stronie (do kolejnego zapytania `before=`), albo `null`, gdy
 * strona jest ostatnia (zapytano o `size + 1` wierszy i otrzymano co najwyzej `size`).
 */
public record CursorPage<T>(List<T> items, Instant nextBefore) {

    /**
     * Buduje strone z wyniku zapytania o {@code size + 1} wierszy (malejaco): nadmiarowy wiersz jest
     * odcinany i jego obecnosc oznacza, ze istnieje kolejna strona.
     */
    public static <T> CursorPage<T> of(List<T> fetched, int size, Function<? super T, Instant> timestampOf) {
        boolean hasMore = fetched.size() > size;
        List<T> page = hasMore ? fetched.subList(0, size) : fetched;
        Instant nextBefore = hasMore ? timestampOf.apply(page.get(page.size() - 1)) : null;
        return new CursorPage<>(List.copyOf(page), nextBefore);
    }
}
