package robert_neat.his_backend.patient;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
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

/**
 * Pacjent (`patient`). `status` utrzymuje wylacznie serwis przy przyjeciu/wypisie. `mrn` nadaje serwis
 * z sekwencji `patient_mrn_seq`. Identyfikator generuje Hibernate (UUID), dzieki czemu `save` robi `persist`.
 */
@Entity
@Table(name = "patient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mrn", nullable = false, length = 30, updatable = false)
    private String mrn;

    @Column(name = "pesel", length = 11)
    private String pesel;

    @Column(name = "no_pesel_reason", length = 30)
    private NoPeselReason noPeselReason;

    @Embedded
    private IdentityDocument identityDocument;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "second_name", length = 100)
    private String secondName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 200)
    private String email;

    @Embedded
    private Address address;

    @Embedded
    private EmergencyContact emergencyContact;

    @Embedded
    private Insurance insurance;

    @Column(name = "blood_type", length = 3)
    private BloodType bloodType;

    @Column(name = "status", nullable = false, length = 15)
    private PatientStatus status;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "patient_flag", joinColumns = @JoinColumn(name = "patient_id"))
    @Column(name = "flag", nullable = false, length = 20)
    @BatchSize(size = 100)
    private Set<PatientFlag> flags = new HashSet<>();

    /** Nowy pacjent w statusie `registered`. */
    public static Patient register(String mrn, PatientCreateRequest data) {
        Patient patient = new Patient();
        patient.mrn = mrn;
        patient.status = PatientStatus.REGISTERED;
        patient.apply(data);
        return patient;
    }

    /** Nadpisuje dane osobowe stanem z zadania (create i scalony PATCH). Nie rusza `mrn` ani `status`. */
    public void apply(PatientCreateRequest d) {
        this.pesel = d.pesel();
        this.noPeselReason = d.pesel() == null ? d.noPeselReason() : null;
        this.identityDocument = d.identityDocument();
        this.firstName = d.firstName();
        this.secondName = d.secondName();
        this.lastName = d.lastName();
        this.birthDate = d.birthDate();
        this.gender = d.gender();
        this.phone = d.phone();
        this.email = d.email();
        this.address = d.address();
        this.emergencyContact = d.emergencyContact();
        this.insurance = d.insurance();
        this.bloodType = d.bloodType();
        Set<PatientFlag> wanted = d.flags() == null || d.flags().isEmpty()
                ? EnumSet.noneOf(PatientFlag.class)
                : EnumSet.copyOf(d.flags());
        this.flags.retainAll(wanted);
        this.flags.addAll(wanted);
    }

    /** Zmiana statusu wynikajaca z przyjecia/wypisu. */
    public void changeStatus(PatientStatus status) {
        this.status = status;
    }
}
