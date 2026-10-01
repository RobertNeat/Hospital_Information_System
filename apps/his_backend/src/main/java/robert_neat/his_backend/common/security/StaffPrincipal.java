package robert_neat.his_backend.common.security;

import java.util.UUID;

/**
 * Znacznik dla principala uwierzytelnienia niosacego `staffId`.
 * Docelowy principal JWT (K3) powinien implementowac ten interfejs (albo zostac zamieniony
 * implementacja {@link CurrentActor}).
 */
public interface StaffPrincipal {

    UUID staffId();
}
