package io.obya.api.onboarding.adapter.out.scorer;

import io.github.microcks.testcontainers.MicrocksContainer;
import io.github.microcks.testcontainers.MicrocksException;
import io.obya.api.onboarding.adapter.out.spectral.SpectralRestAdapter;
import io.obya.api.onboarding.appl.usecase.UsecaseExamples;
import io.obya.api.onboarding.domain.model.*;
import io.obya.common.util.Try;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Integration test that verifies the integration with Scorer/Spectral.
 * Spectral is replaced by a Microcks server so the tests remain self-contained
 * while still validating the HTTP contract with the scorer.
 */
@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
class SpectralRestAdapterTest {

    @Container
    static MicrocksContainer microcksContainer = new MicrocksContainer(
            DockerImageName.parse("quay.io/microcks/microcks-uber:latest"))
                .withDebugLogLevel();

    @Autowired
    SpectralRestAdapter scorer;

    @BeforeAll
    static void importSpecification() throws MicrocksException, IOException {
        microcksContainer.start();
        microcksContainer.importAsMainArtifact( // FIXME: Examples should be a secondary artefact
                new File("target/test-classes/api/scoring/resolved.scoring_v1.openapi.yaml"));
        microcksContainer.importAsSecondaryArtifact(
                new File("target/test-classes/api/scoring/scoring_v1.metadata.yaml"));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("scorer.adapter", () -> "spectral");
        registry.add("spectral.base-url", () -> microcksContainer
                .getRestMockEndpoint("API Scoring - Scoring", "v1")
                .replaceAll("\\s", "+"));
    }

    @Test
    void should_create_specification_resource() {
        Try<Scorecard> result = scorer.score(
                UsecaseExamples.Sources.Oas.validCandidate.get(),
                Contract.Type.OPENAPI);
        printLogs();
        assertThat(result.isSuccess()).isTrue();
    }

    private void printLogs() {
        log.info("LOGS\n-------\n" + microcksContainer.getLogs() + "\n");
    }
}
