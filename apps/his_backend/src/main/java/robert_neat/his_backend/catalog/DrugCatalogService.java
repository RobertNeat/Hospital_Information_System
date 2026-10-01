package robert_neat.his_backend.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.NotFoundException;

/** Katalog lekow (`GET /drugs`, `GET /drugs/{id}`), tylko odczyt. */
@Service
@Transactional(readOnly = true)
public class DrugCatalogService {

    /** Limit wynikow wyszukiwania po stronie serwera (kontrakt: serwer ogranicza liczbe wynikow). */
    public static final int SEARCH_LIMIT = 50;

    private final DrugRepository drugs;

    DrugCatalogService(DrugRepository drugs) {
        this.drugs = drugs;
    }

    /**
     * Leki, ktorych nazwa handlowa, substancja czynna lub ATC zawiera kazdy token `term` (bez wielkosci liter
     * i znakow diakrytycznych). Pusty lub brak `term` to pierwsze {@value #SEARCH_LIMIT} lekow. Wg nazwy rosnaco.
     */
    public List<DrugResponse> search(String term) {
        PageRequest page = PageRequest.of(0, SEARCH_LIMIT, Sort.by("name", "id"));
        return drugs.findAll(DrugSpecifications.matching(term), page).getContent().stream()
                .map(CatalogMapper::toResponse).toList();
    }

    /** 404 dla nieznanego lub niepoprawnego identyfikatora. */
    public DrugResponse get(String id) {
        UUID uuid = parse(id);
        return (uuid == null ? java.util.Optional.<Drug>empty() : drugs.findById(uuid))
                .map(CatalogMapper::toResponse)
                .orElseThrow(() -> NotFoundException.of("Lek", id));
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
