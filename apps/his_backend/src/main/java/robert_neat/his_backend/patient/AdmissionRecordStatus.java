package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `AdmissionRecordStatus` z kontraktu (cykl zycia pojedynczego przyjecia). */
public enum AdmissionRecordStatus implements WireEnum {
    ACTIVE("active"),
    DISCHARGED("discharged"),
    CANCELLED("cancelled");

    private final String wire;

    AdmissionRecordStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<AdmissionRecordStatus> {

        public JpaConverter() {
            super(AdmissionRecordStatus.class);
        }
    }
}
