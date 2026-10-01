package robert_neat.his_backend.terminology.snomed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import robert_neat.his_backend.staff.StaffMember;
import robert_neat.his_backend.staff.StaffMemberRepository;
import robert_neat.his_backend.staff.StaffRole;

class TerminologySuggestionServiceTest {

    private final SnowstormClient client = mock(SnowstormClient.class);
    private final StaffMemberRepository staff = mock(StaffMemberRepository.class);
    private final SuggestionProperties props = new SuggestionProperties(
            new SuggestionProperties.EclSet("<< 1", "<< 2", "<< 3"),
            List.of(new SuggestionProperties.Profile(List.of("kardiologia"), "<< 10", null, "<< 30")));
    private final TerminologySuggestionService service = new TerminologySuggestionService(client,
            new SpecializationEclResolver(props), staff);

    @Test
    void usesSpecializationOfStaffMember() {
        UUID id = UUID.randomUUID();
        StaffMember m = StaffMember.create("lek.", "A", "B", StaffRole.DOCTOR, "Kardiologia", UUID.randomUUID(),
                null, null, "EMP-X", null);
        when(staff.findById(id)).thenReturn(Optional.of(m));
        SnomedConceptPage page = new SnomedConceptPage(1, 0, List.of(new SnomedConcept("1", "x")));
        when(client.expandEcl("<< 10", "ast", 5, 0)).thenReturn(page);

        assertThat(service.suggest(id, TerminologyKind.DIAGNOSIS, "ast", 5)).isSameAs(page);
    }

    @Test
    void missingKindInProfileAndUnknownStaffUseFallback() {
        UUID id = UUID.randomUUID();
        when(staff.findById(id)).thenReturn(Optional.empty());

        service.suggest(id, TerminologyKind.SYMPTOM, " ", 20);
        service.suggest(null, TerminologyKind.PROCEDURE, null, 20);

        verify(client).expandEcl("<< 2", null, 20, 0);
        verify(client).expandEcl("<< 3", null, 20, 0);
    }
}
