package robert_neat.his_backend.ehr;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Slowniki referencyjne. Dane publiczne w obrebie systemu (bez danych pacjenta): wystarczy uwierzytelnienie. */
@RestController
@RequestMapping("/api/v1/dictionaries")
public class DictionaryController {

    private final Icd10DictionaryService icd10;

    DictionaryController(Icd10DictionaryService icd10) {
        this.icd10 = icd10;
    }

    @GetMapping("/icd-10")
    public List<Coding> icd10(@RequestParam(required = false) String term,
            @RequestParam(required = false) Integer size) {
        return icd10.search(term, size);
    }
}
