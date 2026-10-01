package robert_neat.his_backend.staff;

import robert_neat.his_backend.common.wire.WireEnum;

/** `StaffRole` z kontraktu; wartosc na drucie i w kolumnie `staff_member.role` = `wire()`. */
public enum StaffRole implements WireEnum {
    DOCTOR("doctor"),
    NURSE("nurse"),
    LAB_TECHNICIAN("lab_technician"),
    RADIOLOGIST("radiologist"),
    PHARMACIST("pharmacist"),
    REGISTRAR("registrar"),
    ADMIN("admin");

    private final String wire;

    StaffRole(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }
}
