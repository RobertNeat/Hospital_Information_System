package robert_neat.his_backend.vitals;

import java.math.BigDecimal;

/** Reczne mapowanie encja -> DTO. Liczby w JSON bez zbednych zer i bez notacji wykladniczej (`37`, `36.8`). */
final class VitalsMapper {

    private VitalsMapper() {
    }

    static VitalSignsResponse toResponse(VitalSigns v) {
        return new VitalSignsResponse(v.getId(), v.getPatientId(), v.getRecordedAt(), v.getRecordedById(),
                v.getContext(), v.getSource(), v.getDeviceId(), v.getEncounterId(), v.getSystolic(),
                v.getDiastolic(), v.getHeartRate(), plain(v.getTemperature()), v.getSpo2(),
                v.getRespiratoryRate(), v.getPainScore(), v.getNotes());
    }

    static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
