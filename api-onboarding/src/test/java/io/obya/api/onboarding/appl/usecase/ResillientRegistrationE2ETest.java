package io.obya.api.onboarding.appl.usecase;

import io.github.microcks.testcontainers.Assertions;
import io.github.microcks.testcontainers.MicrocksContainer;
import io.github.microcks.testcontainers.MicrocksException;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 This E2E test validates the behavior of the Registration API implementation against the OpenAPI contract.
 The scope covers all the logical layers, ie infrastructure, application, domain.
 The integration involves containerized external dependencies, ie the registry and the scorer.
 Only a few number of nominal tests are eligible.
 */
@Disabled("Still have to be designed")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ResillientRegistrationE2ETest {

    @Container
    static MicrocksContainer microcksContainer = new MicrocksContainer(
            DockerImageName.parse("quay.io/microcks/microcks-uber:latest"))
                .withDebugLogLevel()
                .withAccessToHost(true);

    @LocalServerPort
    Integer port;

    @BeforeAll
    static void importSpecification() throws MicrocksException, IOException {
        microcksContainer.start();
        microcksContainer.importAsMainArtifact(new File( // FIXME: Examples should be a secondary artefact
                "target/test-classes/api/registration/resolved.registration_v1.openapi.yaml"));
        microcksContainer.importAsMainArtifact(new File( // FIXME: Examples should be a secondary artefact
                "target/test-classes/api/strapi/resolved.specification_v1.openapi.yaml"));
        microcksContainer.importAsMainArtifact(new File( // FIXME: Examples should be a secondary artefact
                "target/test-classes/api/scoring/resolved.scoring_v1.openapi.yaml"));
        microcksContainer.importAsSecondaryArtifact(new File(
                "target/test-classes/api/strapi/specification_v1.metadata.faulty.yaml"));
        microcksContainer.importAsSecondaryArtifact(new File(
                "target/test-classes/api/scoring/scoring_v1.metadata.faulty.yaml"));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("registry.adapter", () -> "strapi");
        registry.add("strapi.base-url", () -> microcksContainer
                .getRestMockEndpoint("API Registry - Specification", "v1")
                .replaceAll("\\s", "+"));

        registry.add("scorer.adapter", () -> "spectral");
        registry.add("spectral.base-url", () -> microcksContainer
                .getRestMockEndpoint("API Scoring - Scoring", "v1")
                .replaceAll("\\s", "+"));
    }

    @BeforeEach
    void connectMicrocksWithSUT() {
        Testcontainers.exposeHostPorts(port);
    }

    @Test
    void should_onboarding_usecase_be_conformant_with_contract_and_degrade_gracefully() throws MicrocksException, IOException {
        TestRequest testRequest = new TestRequest.Builder()
                .serviceId("API Onboarding - Registration:v1")
                .runnerType(TestRunnerType.OPEN_API_SCHEMA.name())
                .testEndpoint("http://host.testcontainers.internal:" + port)
                .filteredOperations(List.of("POST /api/v1/registrations"))
                .build();

        TestResult testResult = microcksContainer.testEndpoint(testRequest);

        printLogs();
        print(testResult);

        Assertions.assertSuccess(testResult, "POST /api/v1/registrations");
    }

    private void printLogs() {
        System.out.println("LOGS\n-------\n" + microcksContainer.getLogs() + "\n");
    }

    private void print(TestResult testResult) {
        StringBuffer result = new StringBuffer("RESULTS").append("\n").append("-------").append("\n");
        testResult.getTestCaseResults().forEach(tc -> {
            result.append(String.format("TestCase [%s] status is %s%n", tc.getOperationName(), tc.isSuccess()));
            tc.getTestStepResults().forEach(ts -> {
                if (ts.getMessage() == null || ts.getMessage().isEmpty()) {
                    result.append(String.format("TestStep [%s] status is %s",
                            ts.getRequestName(), ts.isSuccess())).append("\n");
                } else {
                    result.append(String.format("TestStep [%s] status is %s: %s",
                            ts.getRequestName(), ts.isSuccess(), ts.getMessage()));
                }
            });
            result.append("\n");
        });
        System.out.println(result);
    }
}
