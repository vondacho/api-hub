package io.obya.api.onboarding.appl.usecase;

import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.*;
import io.obya.common.util.Try;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;

import static io.obya.api.onboarding.domain.model.Version.V1;

public interface UsecaseExamples {

    interface Sources {
        Supplier<URI> validCandidateUri = () -> uriOf("oas/valid_candidate.openapi.yaml");

        static URI uriOf(String filename) {
            return URI.create("file:///Users/olivier/Labor/github/my-api-portal/api-onboarding/src/test/resources/api/examples/" + filename);
        }

        static String bodyOf(URI uri) {
            try {
                return Files.readString(Paths.get(uri));
            } catch (IOException e) {
                return e.getMessage();
            }
        }
    }

    interface States {
        Supplier<Try<State>> candidateValidated = () -> new Try.Partial<>(new State()
                .source(Sources.validCandidateUri.get())
                .info(new Info(
                        "Petstore API",
                        "A sample API for end-to-end test fixtures.", V1))
                .metadata(new Metadata(
                        "petstore", Revision.V100,
                        "petstore",
                        "platform", null, null))
                .contract(Contract.from(Contract.Version.OPENAPI_V30))
                .score(Scorecard.undefined())
                .body(() -> Sources.bodyOf(Sources.validCandidateUri.get()))
                .status(Status.VALID),
                List.of());

        Supplier<Try<State>> candidateScored = () -> candidateValidated.get().map(s -> s
                .score(DomainExamples.Scores.scorecard.get())
                .status(Status.SCORED));

        Supplier<Try<State>> candidateRegistered = () -> candidateScored.get().map(s -> s
                .id(DomainExamples.Specifications.id123.get())
                .status(Status.REGISTERED));

        Supplier<Try<State>> candidateImplemented = () -> candidateRegistered.get().map(s -> s
                .metadata(new Metadata(
                        "petstore", Revision.V100,
                        "petstore",
                        "platform",
                        "petstore-quarkus", Revision.V100)));

        Supplier<Try<State>> candidateOverlaid = () -> candidateRegistered.get().map(s -> s
                .id(DomainExamples.Specifications.id456.get())
                .metadata(new Metadata(
                        "petstore", Revision.V101,
                        "petstore",
                        "platform", null, null)));

        Supplier<Try<State>> candidateRejected = () -> Try.failure(
                Violation.Code.INSUFFICIENT_SCORING.failure(10).get());

        Supplier<Try<State>> notFound = () -> Try.failure(
                Violation.Code.PROCESSING_FAILED.failure("source", "Resource not found").get());

        Supplier<Try<State>> missingUri = () -> Try.failure(
                Violation.Code.MISSING_DATA.failure("source").get());

        Supplier<Try<State>> missingRevision = () -> Try.failure(
                Violation.Code.MISSING_DATA.failure("revision").get());

        Supplier<Try<State>> malformedUri = () -> Try.failure(
                Violation.Code.MALFORMED_URI.failure("source", "file:// or http://").get());

        Supplier<Try<State>> malformedVersion = () -> Try.failure(
                Violation.Code.MALFORMED_VERSION.failure("version", "vx").get());

        Supplier<Try<State>> malformedRevision = () -> Try.failure(
                Violation.Code.MALFORMED_REVISION.failure("revision", "x.y.z").get());
    }
}
