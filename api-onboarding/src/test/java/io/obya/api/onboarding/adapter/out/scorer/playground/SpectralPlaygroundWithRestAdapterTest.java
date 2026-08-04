package io.obya.api.onboarding.adapter.out.scorer.playground;

import io.obya.api.onboarding.adapter.out.spectral.SpectralRestAdapter;
import io.obya.api.onboarding.appl.usecase.UsecaseExamples;
import io.obya.api.onboarding.domain.model.*;
import io.obya.common.util.Try;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

@Disabled("E2E playground for investigation and troubleshooting")
@SpringBootTest(webEnvironment = NONE)
class SpectralPlaygroundWithRestAdapterTest {

    @DynamicPropertySource
    static void configureStrapiUrl(DynamicPropertyRegistry registry) {
        registry.add("scorer.adapter", () -> "spectral");
        // Scorer instance
        // registry.add("spectral.base-url", () -> "http://localhost:1337/api");
        // Microcks instance
        // registry.add("spectral.base-url", () -> "http://localhost:8585/rest/API+Scoring+-+Scoring/v1");
    }

    @Autowired
    private SpectralRestAdapter scorer;

    @Test
    void should_create_specification_resource() {
        Try<Scorecard> result = scorer.score(
                UsecaseExamples.Sources.Oas.validCandidate.get(),
                Contract.Type.OPENAPI);
        assertThat(result.isSuccess()).isTrue();
    }
}
