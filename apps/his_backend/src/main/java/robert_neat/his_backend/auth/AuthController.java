package robert_neat.his_backend.auth;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import robert_neat.his_backend.security.HisUserPrincipal;
import robert_neat.his_backend.staff.WardService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final RegistrationService registration;
    private final WardService wards;

    AuthController(AuthService auth, RegistrationService registration, WardService wards) {
        this.auth = auth;
        this.registration = registration;
        this.wards = wards;
    }

    /** Publiczna (bez JWT) lista oddzialow do formularza rejestracji: tylko `id`, `name`, `shortName`. */
    @GetMapping("/register/wards")
    public List<PublicWardResponse> registrationWards() {
        return wards.list().stream().map(w -> new PublicWardResponse(w.id(), w.name(), w.shortName())).toList();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /**
     * Bezstanowe: serwer nie przechowuje sesji ani listy uniewaznionych tokenow. Wylogowanie polega na usunieciu
     * tokenu przez frontend; token wygasa sam po TTL. Endpoint istnieje dla symetrii kontraktu.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // brak stanu po stronie serwera
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal HisUserPrincipal principal) {
        return auth.currentUser(principal.staffId());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffRegistrationResponse register(@Valid @RequestBody StaffRegistrationRequest request) {
        return registration.register(request);
    }
}
