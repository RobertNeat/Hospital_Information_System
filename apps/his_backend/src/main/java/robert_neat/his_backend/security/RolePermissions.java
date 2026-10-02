package robert_neat.his_backend.security;

import static robert_neat.his_backend.staff.StaffRole.ADMIN;
import static robert_neat.his_backend.staff.StaffRole.DOCTOR;
import static robert_neat.his_backend.staff.StaffRole.LAB_TECHNICIAN;
import static robert_neat.his_backend.staff.StaffRole.NURSE;
import static robert_neat.his_backend.staff.StaffRole.PHARMACIST;
import static robert_neat.his_backend.staff.StaffRole.RADIOLOGIST;
import static robert_neat.his_backend.staff.StaffRole.REGISTRAR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import robert_neat.his_backend.staff.StaffRole;

/**
 * Macierz uprawnien (R = `*:read`, W = akcje zapisu).
 * Uprawnienie to string `zasob:akcja`; dodatkowo kazda rola dostaje `ROLE_<ROLE>`.
 * Wartosci trafiaja do claima `authorities` tokenu i do `CurrentUser.permissions`.
 */
public final class RolePermissions {

    private static final StaffRole[] ALL = StaffRole.values();

    private static final Map<String, StaffRole[]> MATRIX = new LinkedHashMap<>();
    private static final Map<StaffRole, List<String>> BY_ROLE = new EnumMap<>(StaffRole.class);

    static {
        grant("patient:read", ALL);
        grant("patient:write", REGISTRAR, ADMIN);
        grant("admission:read", DOCTOR, NURSE, REGISTRAR, ADMIN);
        grant("admission:admit", DOCTOR, REGISTRAR, ADMIN);
        grant("admission:discharge", DOCTOR, ADMIN);

        grant("ehr:read", DOCTOR, NURSE, ADMIN);
        grant("ehr:read-limited", LAB_TECHNICIAN, RADIOLOGIST, PHARMACIST);
        grant("ehr:note:write", DOCTOR);
        grant("ehr:note:write-nursing", NURSE);
        grant("ehr:note:write-consultation", RADIOLOGIST);
        grant("ehr:diagnosis:write", DOCTOR);
        grant("ehr:allergy:write", DOCTOR, NURSE);

        grant("lab-order:read", DOCTOR, NURSE, LAB_TECHNICIAN, ADMIN);
        grant("lab-order:create", DOCTOR);
        grant("lab-order:cancel", DOCTOR);
        grant("lab-order:update-status", LAB_TECHNICIAN, ADMIN);
        grant("lab-order:collect-specimen", NURSE);
        grant("lab-result:read", DOCTOR, NURSE, LAB_TECHNICIAN, ADMIN);
        grant("lab-result:acknowledge", DOCTOR);
        grant("lab-result:write", LAB_TECHNICIAN);

        grant("imaging-order:read", DOCTOR, RADIOLOGIST, ADMIN);
        grant("imaging-order:create", DOCTOR);
        grant("imaging-order:cancel", DOCTOR);
        grant("imaging-order:update-status", RADIOLOGIST, ADMIN);
        grant("imaging-result:read", DOCTOR, NURSE, RADIOLOGIST, ADMIN);
        grant("imaging-result:acknowledge", DOCTOR);
        grant("imaging-result:write", RADIOLOGIST);

        grant("prescription:read", DOCTOR, NURSE, PHARMACIST, ADMIN);
        grant("prescription:create", DOCTOR);
        grant("prescription:cancel", DOCTOR);
        grant("drug:read", DOCTOR, NURSE, PHARMACIST, ADMIN);
        grant("drug-safety-check:run", DOCTOR);

        grant("vitals:read", DOCTOR, NURSE, ADMIN);
        grant("vitals:write", DOCTOR, NURSE);
        grant("vital-threshold:read", DOCTOR, NURSE, ADMIN);
        grant("vital-threshold:write", ADMIN);

        grant("message:read", ALL);
        grant("message:write", ALL);
        grant("task:read", DOCTOR, NURSE, ADMIN);
        grant("task:write", DOCTOR, NURSE);
        grant("alert:read", DOCTOR, NURSE, LAB_TECHNICIAN, RADIOLOGIST, ADMIN);
        grant("alert:acknowledge", DOCTOR, NURSE);

        grant("staff:read", ALL);
        grant("staff:write", ADMIN);
        grant("ward:read", ALL);
        grant("ward:write", ADMIN);
        grant("dashboard:read", ALL);
        grant("account:manage", ADMIN);

        for (StaffRole role : ALL) {
            Set<String> sorted = new TreeSet<>();
            MATRIX.forEach((permission, roles) -> {
                if (Arrays.asList(roles).contains(role)) {
                    sorted.add(permission);
                }
            });
            List<String> all = new ArrayList<>();
            all.add("ROLE_" + role.name());
            all.addAll(sorted);
            BY_ROLE.put(role, List.copyOf(all));
        }
    }

    private RolePermissions() {
    }

    private static void grant(String permission, StaffRole... roles) {
        MATRIX.put(permission, roles);
    }

    /** `ROLE_<ROLE>` oraz uprawnienia `zasob:akcja` roli (niemodyfikowalna lista, posortowana). */
    public static List<String> authoritiesOf(StaffRole role) {
        return BY_ROLE.get(role);
    }

    /** Wszystkie uprawnienia znane w macierzy (bez `ROLE_*`). */
    public static Set<String> allPermissions() {
        return Set.copyOf(MATRIX.keySet());
    }

    /** Tylko uprawnienia `zasob:akcja` (bez `ROLE_*`) - do `CurrentUser.permissions`. */
    public static List<String> permissionsOf(StaffRole role) {
        return authoritiesOf(role).stream().filter(a -> !a.startsWith("ROLE_")).toList();
    }
}
