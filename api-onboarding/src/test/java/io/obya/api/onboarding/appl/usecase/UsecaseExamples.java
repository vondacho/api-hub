package io.obya.api.onboarding.appl.usecase;

import io.obya.api.onboarding.appl.usecase.processing.reader.ClasspathResourceReader;
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
        Supplier<URI> unreadableSource = () -> URI.create("file:///nonexistent/does_not_exist.openapi.yaml");
        Supplier<URI> unsupportedSchemeSource = () -> URI.create("ftp://example.com/spec.openapi.yaml");

        /** Addresses an example document on the classpath, so it resolves on any machine. */
        static URI classpathOf(String filename) {
            return URI.create("classpath:///api/examples/" + filename);
        }

        /** OpenAPI example documents. Read with a {@code ClasspathResourceReader}. */
        interface Oas {
            Supplier<URI> validCandidate = () -> Sources.classpathOf("oas/valid_candidate.openapi.yaml");
            Supplier<URI> candidateWithEmptyTitle = () -> Sources.classpathOf("oas/invalid_candidate_missing_required_info.openapi.yaml");
            Supplier<URI> candidateWithoutBundleName = () -> Sources.classpathOf("oas/invalid_candidate_missing_required_metadata.openapi.yaml");
            Supplier<URI> candidateWithoutAnyMetadata = () -> Sources.classpathOf("oas/invalid_candidate_no_metadata.openapi.yaml");
            Supplier<URI> candidateWithMalformedVersion = () -> Sources.classpathOf("oas/invalid_candidate_malformed_version.openapi.yaml");
            Supplier<URI> candidateWithMalformedRevision = () -> Sources.classpathOf("oas/invalid_candidate_malformed_revision.openapi.yaml");
            Supplier<URI> candidateOmittingOptionalInfo = () -> Sources.classpathOf("oas/valid_candidate_missing_non_required_info.openapi.yaml");
            Supplier<URI> candidateOmittingOptionalMetadata = () -> Sources.classpathOf("oas/valid_candidate_missing_non_required_metadata.openapi.yaml");
        }

        /** AsyncAPI example documents. Read with a {@code ClasspathResourceReader}. */
        interface Aas {
            Supplier<URI> validCandidate = () -> Sources.classpathOf("aas/valid_candidate.asyncapi.yaml");
            Supplier<URI> candidateWithEmptyTitle = () -> Sources.classpathOf("aas/invalid_candidate_missing_required_info.asyncapi.yaml");
            Supplier<URI> candidateWithoutBundleName = () -> Sources.classpathOf("aas/invalid_candidate_missing_required_metadata.asyncapi.yaml");
            Supplier<URI> candidateWithoutAnyMetadata = () -> Sources.classpathOf("aas/invalid_candidate_no_metadata.asyncapi.yaml");
            Supplier<URI> candidateWithMalformedVersion = () -> Sources.classpathOf("aas/invalid_candidate_malformed_version.asyncapi.yaml");
            Supplier<URI> candidateWithMalformedRevision = () -> Sources.classpathOf("aas/invalid_candidate_malformed_revision.asyncapi.yaml");
            Supplier<URI> unparseableCandidate = () -> Sources.classpathOf("aas/invalid_candidate_unparseable.asyncapi.yaml");
        }

        /** Overlay example documents. Read with a {@code ClasspathResourceReader}. */
        interface Oai {
            Supplier<URI> validOverlay = () -> Sources.classpathOf("oai/valid.overlay.yaml");
        }

        static String bodyOf(URI uri) {
            try {
                return new ClasspathResourceReader().allInOne(uri);
            } catch (IOException e) {
                return e.getMessage();
            }
        }
    }

    interface Candidates {
        String name = "petstore";
        String productName = "platform";
        String bundleName = "petstore";

        /** Identity declared by the AsyncAPI example documents. */
        interface Aas {
            String apiName = "notification";
            String apiProductName = "platform";
            String apiBundleName = "notification";
        }

        static State candidateOf(Version version, Revision revision) {
            return new State()
                    .source(UsecaseExamples.Sources.Oas.validCandidate.get())
                    .info(new Info(
                            "Petstore API",
                            "A sample API for end-to-end test fixtures.", version))
                    .contract(Contract.from(Contract.Version.OPENAPI_V30))
                    .metadata(new Metadata(name, revision, bundleName, productName, null, null));
        }

        static State candidateOf(Version version, Revision revision, Component component) {
            final State candidate = candidateOf(version, revision);
            return candidate.metadata(candidate.metadata()
                    .withComponent(component.name(), component.revision()));
        }
    }

    interface Registries {
        Supplier<Try<Specification>> nothingRegistered = () -> Try.failure(
                Violation.Code.RESOURCE_NOT_FOUND.failure("Specification",
                        "[%s-%s-%s]".formatted(Candidates.name, Candidates.productName, V1.format())).get());

        static Try<Specification> registeredAt(Revision revision) {
            return Try.success(DomainExamples.Specifications.specificationOf(
                    DomainExamples.Specifications.id123.get(),
                    Candidates.name,
                    Candidates.productName,
                    Version.from(revision),
                    revision,
                    "body"));
        }
    }

    interface States {
        Supplier<Try<State>> candidateValidated = () -> new Try.Partial<>(new State()
                .source(UsecaseExamples.Sources.Oas.validCandidate.get())
                .info(new Info(
                        "Petstore API",
                        "A sample API for end-to-end test fixtures.", V1))
                .metadata(new Metadata(
                        "petstore", Revision.V100,
                        "petstore",
                        "platform", null, null))
                .contract(Contract.from(Contract.Version.OPENAPI_V30))
                .score(Scorecard.undefined())
                .body(() -> UsecaseExamples.Sources.bodyOf(UsecaseExamples.Sources.Oas.validCandidate.get()))
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
