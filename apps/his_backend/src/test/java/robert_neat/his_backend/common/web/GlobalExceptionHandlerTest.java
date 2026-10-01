package robert_neat.his_backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.api.SortWhitelist;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.wire.WireSamples.Blood;
import robert_neat.his_backend.common.wire.WireSamples.OrderStatus;

@WebMvcTest(controllers = {GlobalExceptionHandlerTest.FakeController.class,
        GlobalExceptionHandlerTest.FakeValidatedController.class})
@Import({GlobalExceptionHandlerTest.FakeController.class, GlobalExceptionHandlerTest.FakeValidatedController.class})
@WithMockUser
class GlobalExceptionHandlerTest {

    record Body(@NotBlank String name, @Min(1) int count, Blood blood) {
    }

    @RestController
    @RequestMapping("/fake")
    static class FakeController {

        private static final SortWhitelist SORT = SortWhitelist.of(Sort.by("name"), "name", "count");

        @GetMapping("/not-found")
        String notFound() {
            throw NotFoundException.of("Pacjent", "x1");
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new ConflictException("Pacjent ma juz aktywne przyjecie");
        }

        @GetMapping("/optimistic")
        String optimistic() {
            throw new ObjectOptimisticLockingFailureException("Patient", 1L);
        }

        @GetMapping("/unique")
        String unique() {
            throw new DataIntegrityViolationException("dup",
                    new RuntimeException(new SQLException("duplicate key", "23505")));
        }

        @GetMapping("/fk")
        String foreignKey() {
            throw new DataIntegrityViolationException("fk", new SQLException("fk", "23503"));
        }

        @GetMapping("/denied")
        String denied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/authz-denied")
        String authzDenied() {
            throw new AuthorizationDeniedException("nope", new AuthorizationDecision(false));
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("tajne-szczegoly-bazy");
        }

        @GetMapping("/app-validation")
        String appValidation() {
            throw new ValidationFailedException("slotId", "slot jest zajety");
        }

        @GetMapping("/status")
        String status(@RequestParam(required = false) OrderStatus status, @RequestParam(required = false) Blood blood) {
            return (status == null ? "-" : status.wire()) + "|" + (blood == null ? "-" : blood.wire());
        }

        @GetMapping("/required")
        String required(@RequestParam String term) {
            return term;
        }

        @GetMapping("/min")
        String min(@RequestParam @Min(1) int size) {
            return "ok";
        }

        @GetMapping("/page")
        PageResponse<String> page(Pageable pageable) {
            Pageable p = SORT.apply(pageable);
            return PageResponse.from(new PageImpl<>(List.of("x"), p, 1));
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody Body body) {
            return "ok";
        }
    }

    @RestController
    @RequestMapping("/fake-validated")
    @Validated
    static class FakeValidatedController {

        @GetMapping("/min")
        String min(@RequestParam @Min(1) int size) {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void notFoundIs404() throws Exception {
        mvc.perform(get("/fake/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.type").value("urn:his:problem:not-found"))
                .andExpect(jsonPath("$.detail").value("Pacjent o identyfikatorze 'x1' nie istnieje"))
                .andExpect(jsonPath("$.instance").value("/fake/not-found"));
    }

    @Test
    void conflictIs409() throws Exception {
        mvc.perform(get("/fake/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void optimisticLockIs409() throws Exception {
        mvc.perform(get("/fake/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void uniqueViolationIs409OtherIntegrityIs500() throws Exception {
        mvc.perform(get("/fake/unique"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(get("/fake/fk"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"));
    }

    @Test
    void accessDeniedAndAuthorizationDeniedAre403() throws Exception {
        mvc.perform(get("/fake/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/fake/authz-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unexpectedExceptionIs500WithoutDetails() throws Exception {
        mvc.perform(get("/fake/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(content().string(not(containsString("tajne-szczegoly-bazy"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at robert_neat"))));
    }

    @Test
    void applicationValidationIs422WithErrors() throws Exception {
        mvc.perform(get("/fake/app-validation"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("slotId"))
                .andExpect(jsonPath("$.errors[0].message").value("slot jest zajety"));
    }

    @Test
    void wireEnumQueryParamsAreConverted() throws Exception {
        mvc.perform(get("/fake/status").param("status", "specimen_collected").param("blood", "0+"))
                .andExpect(status().isOk())
                .andExpect(content().string("specimen_collected|0+"));
    }

    @Test
    void invalidEnumQueryParamIs422() throws Exception {
        mvc.perform(get("/fake/status").param("status", "bogus"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message", containsString("specimen_collected")));
    }

    @Test
    void missingRequiredParamIs422() throws Exception {
        mvc.perform(get("/fake/required"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("term"));
    }

    @Test
    void methodConstraintOnRequestParamIs422() throws Exception {
        mvc.perform(get("/fake/min").param("size", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void constraintViolationExceptionIs422() throws Exception {
        mvc.perform(get("/fake-validated/min").param("size", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void beanValidationOnBodyIs422WithFieldErrors() throws Exception {
        mvc.perform(post("/fake/body").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"count\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='name')].code").value("NotBlank"))
                .andExpect(jsonPath("$.errors[?(@.field=='count')].message").exists());
    }

    @Test
    void invalidEnumInBodyIs422() throws Exception {
        mvc.perform(post("/fake/body").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"count\":1,\"blood\":\"C+\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("blood"))
                .andExpect(jsonPath("$.errors[0].message", containsString("0+")));
    }

    @Test
    void malformedJsonIs422() throws Exception {
        mvc.perform(post("/fake/body").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{nie-json"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void validBodyPasses() throws Exception {
        mvc.perform(post("/fake/body").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"count\":1,\"blood\":\"0+\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void pageSizeIsCappedAtHundred() throws Exception {
        mvc.perform(get("/fake/page").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0]").value("x"));
    }

    @Test
    void pageDefaultsAndAllowedSort() throws Exception {
        mvc.perform(get("/fake/page").param("sort", "count,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void unknownSortFieldIs422() throws Exception {
        mvc.perform(get("/fake/page").param("sort", "password,asc"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void unknownPathIs404Problem() throws Exception {
        mvc.perform(get("/fake/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodIs405WithoutCode() throws Exception {
        mvc.perform(post("/fake/not-found").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void anonymousAccessDeniedFromMethodSecurityIs401() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        try {
            var response = new GlobalExceptionHandler().accessDenied(new AccessDeniedException("x"));
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getBody().getProperties()).containsEntry("code",
                    robert_neat.his_backend.common.api.ApiErrorCode.UNAUTHENTICATED);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
