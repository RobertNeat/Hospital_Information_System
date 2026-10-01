package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AdmissionStatus` z kontraktu (status pacjenta; pochodna stanu przyjec, utrzymywana przez serwis). */
public enum PatientStatus implements WireEnum {
    REGISTERED("registered"),
    ADMITTED("admitted"),
    OUTPATIENT("outpatient"),
    DISCHARGED("discharged");

    private final String wire;

    PatientStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<PatientStatus> {

        public JpaConverter() {
            super(PatientStatus.class);
        }
    }
}
