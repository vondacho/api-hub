package io.obya.api.onboarding.adapter.out.strapi;

import io.github.microcks.testcontainers.MicrocksContainer;
import io.github.microcks.testcontainers.MicrocksException;
import io.obya.api.onboarding.appl.usecase.UsecaseExamples;
import io.obya.api.onboarding.domain.model.Revision;
import io.obya.api.onboarding.domain.model.Specification;
import io.obya.api.onboarding.domain.model.SpecificationId;
import io.obya.api.onboarding.domain.model.Version;
import io.obya.common.util.Try;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
 * Integration test that verifies the resilience of the integration with Strapi registry.
 * Strapi is replaced by a Microcks server so the tests remain self-contained
 * while still validating the HTTP contract with the registry.
 */
@Disabled("Still have to be designed")
@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ResilientStrapiRegistryRestAdapterTest {

    @Container
    static MicrocksContainer microcksContainer = new MicrocksContainer(
            DockerImageName.parse("quay.io/microcks/microcks-uber:latest"))
                .withDebugLogLevel();

    @Autowired
    ResilientStrapiRegistryRestAdapter registry;

    @BeforeAll
    static void importSpecification() throws MicrocksException, IOException {
        microcksContainer.start();
        microcksContainer.importAsMainArtifact( // FIXME: Examples should be a secondary artefact
                new File("target/test-classes/api/strapi/resolved.specification_v1.openapi.yaml"));
        microcksContainer.importAsSecondaryArtifact(
                new File("target/test-classes/api/strapi/specification_v1.metadata.faulty.yaml"));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("registry.adapter", () -> "strapi");
        registry.add("strapi.base-url", () -> microcksContainer
                .getRestMockEndpoint("API Registry - Specification", "v1")
                .replaceAll("\\s", "+"));
    }

    @Test
    void should_create_specification_resource_degrade_gracefully() {
        Try<SpecificationId> result = registry.register(
                UsecaseExamples.States.candidateScored.get().getOrThrow().toSpecification());
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void should_update_specification_resource_degrade_gracefully() {
        Try<SpecificationId> result = registry.register(
                UsecaseExamples.States.candidateRegistered.get().getOrThrow().toSpecification());
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void should_get_specification_resource_with_latest_revision_degrade_gracefully() {
        Try<Specification> result = registry.latestAt("petstore", "platform", Version.V1);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void should_get_specification_resource_with_revision_degrade_gracefully() {
        Try<Specification> result = registry.revisionAt("petstore", "platform", Version.V1, Revision.V100);
        assertThat(result.isSuccess()).isTrue();
    }

}
