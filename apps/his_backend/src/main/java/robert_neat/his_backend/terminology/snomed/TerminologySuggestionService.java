package robert_neat.his_backend.terminology.snomed;

import java.util.UUID;

import org.springframework.stereotype.Service;

import robert_neat.his_backend.staff.StaffMemberRepository;

/** Podpowiedzi SNOMED CT zawezone ECL-em wynikajacym ze specjalizacji zalogowanego pracownika. */
@Service
public class TerminologySuggestionService {

    private final SnowstormClient client;
    private final SpecializationEclResolver resolver;
    private final StaffMemberRepository staff;

    public TerminologySuggestionService(SnowstormClient client, SpecializationEclResolver resolver,
            StaffMemberRepository staff) {
        this.client = client;
        this.resolver = resolver;
        this.staff = staff;
    }

    /** `staffId` null (np. brak principala HIS) lub nieznany pracownik -> zestaw domyslny. */
    public SnomedConceptPage suggest(UUID staffId, TerminologyKind kind, String term, int size) {
        String specialization = staffId == null ? null
                : staff.findById(staffId).map(s -> s.getSpecialization()).orElse(null);
        String ecl = resolver.resolve(specialization, kind);
        return client.expandEcl(ecl, term == null || term.isBlank() ? null : term, size, 0);
    }
}
