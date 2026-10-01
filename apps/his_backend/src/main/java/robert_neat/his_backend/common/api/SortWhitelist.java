package robert_neat.his_backend.common.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Whitelista pol sortowania per endpoint: nazwa na drucie (`sort=lastName,asc`) -> wlasciwosc encji.
 * Nieznane pole = 422 VALIDATION_FAILED (`errors[0].field = "sort"`). Brak sortowania w zadaniu = sort domyslny.
 */
public final class SortWhitelist {

    private final Map<String, String> allowed;
    private final Sort defaultSort;

    private SortWhitelist(Map<String, String> allowed, Sort defaultSort) {
        this.allowed = allowed;
        this.defaultSort = defaultSort;
    }

    /** Pola o tej samej nazwie na drucie i w encji. */
    public static SortWhitelist of(Sort defaultSort, String... fields) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String f : fields) {
            map.put(f, f);
        }
        return new SortWhitelist(map, defaultSort);
    }

    public static SortWhitelist of(Sort defaultSort, Map<String, String> wireToProperty) {
        return new SortWhitelist(new LinkedHashMap<>(wireToProperty), defaultSort);
    }

    /** Zwraca Pageable z sortem przemapowanym na wlasciwosci encji albo rzuca ValidationFailedException. */
    public Pageable apply(Pageable requested) {
        if (requested.isUnpaged()) {
            return requested;
        }
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), resolve(requested.getSort()));
    }

    public Sort resolve(Sort requested) {
        if (requested == null || requested.isUnsorted()) {
            return defaultSort;
        }
        List<Sort.Order> orders = new ArrayList<>();
        List<FieldError> errors = new ArrayList<>();
        for (Sort.Order o : requested) {
            String property = allowed.get(o.getProperty());
            if (property == null) {
                errors.add(new FieldError("sort",
                        "Niedozwolone pole sortowania '" + o.getProperty() + "'; dozwolone: "
                                + String.join(", ", allowed.keySet())));
            } else {
                orders.add(new Sort.Order(o.getDirection(), property));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return Sort.by(orders);
    }
}
