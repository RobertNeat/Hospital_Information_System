package robert_neat.his_backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SortWhitelistTest {

    private final SortWhitelist whitelist = SortWhitelist.of(Sort.by("lastName", "id"), Map.of(
            "lastName", "lastName", "ward", "wardId"));

    @Test
    void unsortedRequestGetsDefaultSort() {
        Pageable p = whitelist.apply(PageRequest.of(0, 20));
        assertThat(p.getSort()).isEqualTo(Sort.by("lastName", "id"));
        assertThat(p.getPageSize()).isEqualTo(20);
    }

    @Test
    void wireNamesAreMappedToEntityProperties() {
        Pageable p = whitelist.apply(PageRequest.of(2, 5, Sort.by(Sort.Order.desc("ward"))));
        assertThat(p.getSort()).isEqualTo(Sort.by(Sort.Order.desc("wardId")));
        assertThat(p.getPageNumber()).isEqualTo(2);
    }

    @Test
    void unknownFieldIsValidationFailure() {
        assertThatThrownBy(() -> whitelist.apply(PageRequest.of(0, 20, Sort.by("password"))))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> {
                    assertThat(e.getErrors()).hasSize(1);
                    assertThat(e.getErrors().get(0).field()).isEqualTo("sort");
                    assertThat(e.getErrors().get(0).message()).contains("password");
                });
    }

    @Test
    void pageResponseCopiesPageMetadata() {
        var page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);
        PageResponse<String> r = PageResponse.from(page, i -> "n" + i);
        assertThat(r).isEqualTo(new PageResponse<>(List.of("n1", "n2"), 1, 2, 5L, 3));
    }
}
