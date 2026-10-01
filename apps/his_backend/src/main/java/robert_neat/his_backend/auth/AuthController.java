package robert_neat.his_backend.auth;

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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final RegistrationService registration;

    AuthController(AuthService auth, RegistrationService registration) {
        this.auth = auth;
        this.registration = registration;
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
