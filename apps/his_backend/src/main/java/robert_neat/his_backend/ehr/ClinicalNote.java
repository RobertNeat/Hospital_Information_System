package robert_neat.his_backend.ehr;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.common.persistence.VersionedEntity;

/** Notatka kliniczna (`clinical_note`) z objawami (`clinical_note_symptom`). FK mapowane skalarnie. */
@Entity
@Table(name = "clinical_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalNote extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "category", nullable = false, length = 15)
    private NoteCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "clinical_note_symptom", joinColumns = @JoinColumn(name = "note_id"))
    @Column(name = "symptom", nullable = false, length = 200)
    @BatchSize(size = 100)
    private Set<String> symptoms = new HashSet<>();

    public static ClinicalNote create(UUID patientId, UUID encounterId, UUID authorId, NoteCategory category,
            String title, String content, Set<String> symptoms) {
        ClinicalNote note = new ClinicalNote();
        note.patientId = patientId;
        note.encounterId = encounterId;
        note.authorId = authorId;
        note.category = category;
        note.title = title;
        note.content = content;
        note.symptoms.addAll(symptoms);
        return note;
    }
}
