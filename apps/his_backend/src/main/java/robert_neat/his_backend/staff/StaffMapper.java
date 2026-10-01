package robert_neat.his_backend.staff;

/** Reczne mapowanie encja -> DTO (bez MapStruct). */
public final class StaffMapper {

    private StaffMapper() {
    }

    public static WardResponse toResponse(Ward w) {
        return new WardResponse(w.getId(), w.getName(), w.getShortName(), w.getFloor(), w.getBeds());
    }

    public static StaffMemberResponse toResponse(StaffMember s, StaffAccountStatus accountStatus, boolean online) {
        return new StaffMemberResponse(s.getId(), s.getTitle(), s.getFirstName(), s.getLastName(), s.getRole(),
                s.getSpecialization(), s.getWardId(), s.getPhone(), s.getPwz(), s.getEmployeeId(), s.getEmail(),
                accountStatus, online);
    }
}
