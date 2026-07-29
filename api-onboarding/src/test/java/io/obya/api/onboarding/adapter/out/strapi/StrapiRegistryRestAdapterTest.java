package io.obya.api.onboarding.adapter.out.strapi;

import io.github.microcks.testcontainers.MicrocksContainer;
import io.github.microcks.testcontainers.MicrocksException;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Integration test that verifies the integration with Strapi registry.
 * Strapi is replaced by a Microcks server so the tests remain self-contained
 * while still validating the HTTP contract with the registry.
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
class StrapiRegistryRestAdapterTest {

    @Container
    static MicrocksContainer microcksContainer = new MicrocksContainer(
            DockerImageName.parse("quay.io/microcks/microcks-uber:latest"))
                .withDebugLogLevel();

    @Autowired
    StrapiRegistryRestAdapter registry;

    @BeforeAll
    static void importSpecification() throws MicrocksException, IOException {
        microcksContainer.start();
        microcksContainer.importAsMainArtifact( // FIXME: Examples should be a secondary artefact
                new File("target/test-classes/api/strapi/resolved.specification_v1.openapi.yaml"));
        microcksContainer.importAsSecondaryArtifact(
                new File("target/test-classes/api/strapi/specification_v1.metadata.yaml"));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("registry.adapter", () -> "strapi");
        registry.add("strapi.base-url", () -> microcksContainer
                .getRestMockEndpoint("API Registry - Specification", "v1")
                .replaceAll("\\s", "+"));
    }

    @Order(1)
    @Test
    void should_create_specification_resource() {
        Try<SpecificationId> result = registry.register(
                UsecaseExamples.States.candidateScored.get().getOrThrow().toSpecification());
        printLogs();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrThrow()).isEqualTo(DomainExamples.Specifications.id123.get());
    }

    @Order(2)
    @Test
    void should_get_all_specification_resources() {
        Try<List<Specification>> result = registry.all();
        printLogs();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrThrow()).hasSize(2);
    }

    @Order(3)
    @Test
    void should_get_specification_resource_with_latest_revision() {
        Try<Specification> result = registry.latestAt("petstore", "platform", Version.V1);
        printLogs();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrThrow().id()).isEqualTo(DomainExamples.Specifications.id456.get());
    }

    @Order(4)
    @Test
    void should_get_specification_resource_with_revision_100() {
        Try<Specification> result = registry.revisionAt("petstore", "platform", Version.V1, Revision.V100);
        printLogs();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrThrow().id()).isEqualTo(DomainExamples.Specifications.id123.get());
    }

    @Order(5)
    @Test
    void should_get_specification_resource_with_revision_101() {
        Try<Specification> result = registry.revisionAt("petstore", "platform", Version.V1, Revision.V101);
        printLogs();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrThrow().id()).isEqualTo(DomainExamples.Specifications.id456.get());
    }

    @Order(6)
    @Test
    void should_update_specification_resource() {
        Try<SpecificationId> result = registry.register(
                UsecaseExamples.States.candidateRegistered.get().getOrThrow().toSpecification());
        printLogs();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrThrow()).isEqualTo(DomainExamples.Specifications.id123.get());
    }

    private void printLogs() {
        log.info("LOGS\n-------\n" + microcksContainer.getLogs() + "\n");
    }
}
