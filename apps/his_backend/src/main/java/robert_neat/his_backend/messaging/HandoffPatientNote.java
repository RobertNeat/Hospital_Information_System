package robert_neat.his_backend.messaging;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Notatka SBAR o jednym pacjencie (`handoff_patient_note`); osadzana kolekcja w {@link HandoffNote}. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoffPatientNote {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "situation", nullable = false, columnDefinition = "text")
    private String situation;

    @Column(name = "background", nullable = false, columnDefinition = "text")
    private String background;

    @Column(name = "assessment", nullable = false, columnDefinition = "text")
    private String assessment;

    @Column(name = "recommendation", nullable = false, columnDefinition = "text")
    private String recommendation;

    static HandoffPatientNote of(UUID patientId, String situation, String background, String assessment,
            String recommendation) {
        HandoffPatientNote n = new HandoffPatientNote();
        n.patientId = patientId;
        n.situation = situation;
        n.background = background;
        n.assessment = assessment;
        n.recommendation = recommendation;
        return n;
    }
}
