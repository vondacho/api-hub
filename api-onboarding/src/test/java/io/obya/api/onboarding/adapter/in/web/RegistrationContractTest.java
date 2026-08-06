package io.obya.api.onboarding.adapter.in.web;

import io.github.microcks.testcontainers.Assertions;
import io.github.microcks.testcontainers.MicrocksContainer;
import io.github.microcks.testcontainers.MicrocksException;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import io.obya.api.onboarding.appl.usecase.UsecaseExamples;
import io.obya.api.onboarding.appl.usecase.RegistrationService;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.SpecificationId;
import io.obya.common.util.Try;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 This test validates the conformance of the Registration API implementation against the OpenAPI contract.
 The scope is the infrastructure layer.
 The SUT integrates the RegistrationRestAdapter, the serializers/deserializers, and the OpenAPI validator.
 The setup includes Microcks playing the contract examples against the SUT backed by and a mocked RegistrationService
 driven by examples.
 The test verifies that the SUT behaves as expected during interaction examples.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class RegistrationContractTest {

    @Container
    static MicrocksContainer microcksContainer = new MicrocksContainer(
            DockerImageName.parse("quay.io/microcks/microcks-uber:latest"))
                .withDebugLogLevel()
                .withAccessToHost(true);

    @LocalServerPort
    Integer port;

    @MockitoBean
    RegistrationService usecase;

    @BeforeAll
    static void importSpecification() throws MicrocksException, IOException {
        microcksContainer.start();
        microcksContainer.importAsMainArtifact(
                // FIXME: Examples should be secondary artefacts
                new File("target/test-classes/api/registration/resolved.registration_v1.openapi.yaml"));
    }

    @BeforeEach
    void connectMicrocksWithSUT() {
        Testcontainers.exposeHostPorts(port);
    }

    @Test
    void should_registration_be_conformant_against_contract() throws MicrocksException, IOException {
        mockSubmission(Map.of(
            "valid_candidate", UsecaseExamples.States.candidateValidated,
            "scored_candidate", UsecaseExamples.States.candidateScored,
            "registered_candidate", UsecaseExamples.States.candidateRegistered,
            "rejected_candidate", UsecaseExamples.States.candidateRejected,
            "not_found", UsecaseExamples.States.notFound
        ));
        mockScoring(Map.of(
            "123", UsecaseExamples.States.candidateRegistered,
            "not-found", UsecaseExamples.States.notFound
        ));
        mockImplementation(Map.of(
            "123", UsecaseExamples.States.candidateImplemented,
            "not-found", UsecaseExamples.States.notFound,
            "missing-revision", UsecaseExamples.States.missingRevision,
            "malformed-revision", UsecaseExamples.States.malformedRevision
        ));
        mockOverlaying(Map.of(
            "123", UsecaseExamples.States.candidateOverlaid,
            "not-found", UsecaseExamples.States.notFound,
            "missing-uri", UsecaseExamples.States.missingUri,
            "malformed-uri", UsecaseExamples.States.malformedUri,
            "overlay-not-found", UsecaseExamples.States.notFound
        ));

        TestRequest testRequest = new TestRequest.Builder()
                .serviceId("API Onboarding - Registration:v1")
                .runnerType(TestRunnerType.OPEN_API_SCHEMA.name())
                .testEndpoint("http://host.testcontainers.internal:" + port)
                .filteredOperations(List.of(
                        "POST /api/v1/registrations",
                        "PUT /api/v1/registrations/{id}/score",
                        "PUT /api/v1/registrations/{id}/component",
                        "POST /api/v1/registrations/{id}/overlay"
                ))
                .build();

        TestResult testResult = microcksContainer.testEndpoint(testRequest);

        printLogs();
        print(testResult);

        Assertions.assertSuccess(testResult, "POST /api/v1/registrations");
        Assertions.assertSuccess(testResult, "PUT /api/v1/registrations/{id}/score");
        Assertions.assertSuccess(testResult, "PUT /api/v1/registrations/{id}/component");
        Assertions.assertSuccess(testResult, "POST /api/v1/registrations/{id}/overlay");
    }

    private void mockSubmission(Map<String, Supplier<Try<State>>> examples) {
        when(usecase.submit(any())).thenAnswer(i -> {
            var uri = i.getArgument(0, URI.class);
            for (var entry : examples.entrySet()) {
                if (uri.getPath().contains(entry.getKey()))
                    return entry.getValue().get();
            }
            throw new IllegalStateException("Unexpected value: " + uri);
        });
    }

    private void mockScoring(Map<String, Supplier<Try<State>>> examples) {
        when(usecase.score(any())).thenAnswer(i -> {
            var id = i.getArgument(0, SpecificationId.class);
            for (var entry : examples.entrySet()) {
                if (id.id().equals(entry.getKey()))
                    return entry.getValue().get();
            }
            throw new IllegalStateException("Unexpected value: " + id);
        });
    }

    private void mockImplementation(Map<String, Supplier<Try<State>>> examples) {
        when(usecase.implement(any(), any())).thenAnswer(i -> {
            var id = i.getArgument(0, SpecificationId.class);
            for (var entry : examples.entrySet()) {
                if (id.id().equals(entry.getKey()))
                    return entry.getValue().get();
            }
            throw new IllegalStateException("Unexpected value: " + id);
        });
    }

    private void mockOverlaying(Map<String, Supplier<Try<State>>> examples) {
        when(usecase.overlay(any(), any())).thenAnswer(i -> {
            var id = i.getArgument(0, SpecificationId.class);
            for (var entry : examples.entrySet()) {
                if (id.id().equals(entry.getKey()))
                    return entry.getValue().get();
            }
            throw new IllegalStateException("Unexpected value: " + id);
        });
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
