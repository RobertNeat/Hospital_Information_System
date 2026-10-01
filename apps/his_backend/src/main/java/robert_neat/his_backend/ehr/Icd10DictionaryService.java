package robert_neat.his_backend.ehr;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ValidationFailedException;

/** Slownik ICD-10 (`GET /dictionaries/icd-10`): wyszukiwanie po kodzie i nazwie z limitem po stronie serwera. */
@Service
@Transactional(readOnly = true)
public class Icd10DictionaryService {

    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 100;

    private final Icd10CodeRepository codes;

    Icd10DictionaryService(Icd10CodeRepository codes) {
        this.codes = codes;
    }

    /** `size` > {@value #MAX_SIZE} jest obcinane (jak paginacja); `size` < 1 to 422. Wyniki wg kodu rosnaco. */
    public List<Coding> search(String term, Integer size) {
        int limit = size == null ? DEFAULT_SIZE : size;
        if (limit < 1) {
            throw new ValidationFailedException("size", "Rozmiar musi byc dodatni");
        }
        PageRequest page = PageRequest.of(0, Math.min(limit, MAX_SIZE), Sort.by("code"));
        return codes.findAll(Icd10Specifications.matching(term), page).getContent().stream()
                .map(c -> new Coding(CodingSystem.ICD_10, c.getCode(), c.getDisplay())).toList();
    }
}
