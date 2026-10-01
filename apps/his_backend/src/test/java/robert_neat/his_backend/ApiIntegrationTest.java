package robert_neat.his_backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wspolna baza testow integracyjnych API: pelny kontekst Springa + Testcontainers (postgres:17-alpine)
 * + dane `reference,mock` + MockMvc. Kolejne moduly dziedzicza te klase (nazwa podklasy musi konczyc sie na
 * `Test`/`Tests`), dzieki czemu wszystkie wspoldziela JEDEN kontekst Springa (i jeden kontener).
 * <p>
 * Zasady, zeby nie rozbic cache'u kontekstow:
 * <ul>
 *   <li>nie dodawac w podklasach {@code @MockitoBean}/{@code @MockBean}, {@code @TestPropertySource},
 *       {@code @ActiveProfiles} ani wlasnych {@code properties} w {@code @SpringBootTest};</li>
 *   <li>testy sa {@code @Transactional} (rollback po tescie), wiec zapisy z testow nie przeciekaja;
 *       dane mock z Liquibase sa wspolne i tylko do odczytu;</li>
 *   <li>uwierzytelnienie: prawdziwy token z {@code POST /api/v1/auth/login} (naglowek Bearer) albo
 *       {@code @WithMockUser(authorities = ...)} z uprawnieniami wymaganymi przez {@code @PreAuthorize};
 *       API jest bezstanowe (JWT), wiec zadania modyfikujace NIE wymagaja {@code csrf()};</li>
 *   <li>konta demo (login = haslo, patrz {@link DemoAccounts}) sa zawsze w bazie; ich liczba wchodzi do asercji
 *       licznosci oddzialow/pracownikow/kont.</li>
 * </ul>
 */
@SpringBootTest(properties = "spring.liquibase.contexts=reference,mock")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class ApiIntegrationTest {
}
