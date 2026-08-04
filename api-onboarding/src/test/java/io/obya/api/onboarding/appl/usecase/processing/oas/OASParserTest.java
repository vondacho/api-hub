package io.obya.api.onboarding.appl.usecase.processing.oas;

import io.obya.api.onboarding.appl.usecase.processing.Processor;
import io.obya.api.onboarding.appl.usecase.processing.reader.ClasspathResourceReader;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIFileReader;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.Info;
import io.obya.api.onboarding.domain.model.Metadata;
import io.obya.api.onboarding.domain.model.Violation;
import io.obya.common.util.Try;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Candidates.bundleName;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Candidates.name;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Candidates.productName;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.Oas.*;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.unreadableSource;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.unsupportedSchemeSource;
import static io.obya.api.onboarding.domain.model.Version.V1;
import static io.obya.api.onboarding.domain.model.Violation.Code.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioural specification of the {@link OASParser} template, exercised through its
 * {@link OASV30Parser} incarnation.
 *
 * <p>The parser turns a source URI into a populated workflow {@link State}: it reads the document,
 * lifts the OpenAPI {@code info} block into {@link Info}, lifts the {@code x-} onboarding
 * extensions into {@link Metadata}, and writes the resolved document into the state body. What it
 * refuses is as much part of its contract as what it produces:</p>
 * <ul>
 *   <li>an info field or onboarding extension that is required but empty is rejected with
 *       {@code MISSING_DATA};</li>
 *   <li>an API version that is not {@code vN} is rejected with {@code MALFORMED_VERSION}, and a
 *       revision that is not semver with {@code MALFORMED_REVISION};</li>
 *   <li>a source that no reader can resolve is rejected with {@code PROCESSING_FAILED};</li>
 *   <li>a state carrying no source at all is rejected with {@code MISSING_DATA};</li>
 *   <li>an already-failed incoming state is passed through untouched, unparsed.</li>
 * </ul>
 *
 * <p>The revision is deliberately <em>not</em> defaulted here — a document that declares none
 * leaves it unset for the {@code Revisor} to assign.</p>
 *
 * <p>Tests assert on the resulting {@code Try<State>}. Nothing is mocked: the documents are the
 * real example specifications, addressed on the classpath so they resolve on any machine, and the
 * OpenAPI parsing library is exercised for real — it is part of the job, not a collaborator.</p>
 */
class OASParserTest {

    private static Processor<State> parser() {
        return new OASV30Parser(new ClasspathResourceReader(), new URIFileReader());
    }

    private static Try<State> parse(URI source) {
        return parser().process(Try.success(new State().source(source)));
    }

    private static State parsed(Try<State> result) {
        assertTrue(result.isSuccess(), () -> "expected a success but was: " + result);
        return result.getValue().orElseThrow();
    }

    private static Violation.Code onlyRejection(Try<State> result) {
        assertTrue(result.isFailure(), () -> "expected a failure but was: " + result);
        assertEquals(1, result.getExceptions().size(),
                () -> "expected exactly one violation but was: " + result.getExceptions());
        Exception e = result.getExceptions().getFirst();
        assertInstanceOf(Violation.Failure.class, e);
        return ((Violation.Failure) e).code;
    }

    @Nested
    class PopulatesTheStateFromTheDocument {

        @Test
        void liftsTheInfoBlockOfTheDocument() {
            Info info = parsed(parse(validCandidate.get())).info();

            assertEquals("Petstore API", info.title());
            assertEquals("A sample API for end-to-end test fixtures.", info.description());
            assertEquals(V1, info.version());
        }

        @Test
        void liftsTheOnboardingExtensionsIntoTheMetadata() {
            Metadata metadata = parsed(parse(validCandidate.get())).metadata();

            assertEquals(name, metadata.name());
            assertEquals(bundleName, metadata.bundleName());
            assertEquals(productName, metadata.productName());
        }

        @Test
        void leavesTheRevisionUnassignedWhenTheDocumentDeclaresNone() {
            Metadata metadata = parsed(parse(validCandidate.get())).metadata();

            assertNull(metadata.revision(),
                    "an undeclared revision is the Revisor's to assign, not the parser's");
        }

        @Test
        void leavesTheComponentUnassignedWhenTheDocumentDeclaresNone() {
            Metadata metadata = parsed(parse(validCandidate.get())).metadata();

            assertNull(metadata.componentName());
            assertNull(metadata.componentRevision());
        }

        @Test
        void writesTheResolvedDocumentIntoTheBody() {
            String body = parsed(parse(validCandidate.get())).body().get();

            assertNotNull(body);
            assertTrue(body.contains("Petstore API"),
                    () -> "the body should carry the resolved document but was: " + body);
        }
    }

    @Nested
    class RejectsAnIncompleteDocument {

        @Test
        void reportsMissingDataWhenARequiredInfoFieldIsEmpty() {
            assertEquals(MISSING_DATA, onlyRejection(parse(candidateWithEmptyTitle.get())));
        }

        @Test
        void reportsMissingDataWhenARequiredOnboardingExtensionIsAbsent() {
            assertEquals(MISSING_DATA, onlyRejection(parse(candidateWithoutBundleName.get())));
        }

        @Test
        void reportsMissingDataWhenTheDocumentCarriesNoOnboardingMetadataAtAll() {
            assertEquals(MISSING_DATA, onlyRejection(parse(candidateWithoutAnyMetadata.get())));
        }
    }

    @Nested
    class RejectsMalformedValues {

        @Test
        void reportsMalformedVersionWhenTheApiVersionIsNotAMajorVersion() {
            assertEquals(MALFORMED_VERSION, onlyRejection(parse(candidateWithMalformedVersion.get())));
        }

        @Test
        void reportsMalformedRevisionWhenTheApiRevisionIsNotSemver() {
            assertEquals(MALFORMED_REVISION, onlyRejection(parse(candidateWithMalformedRevision.get())));
        }
    }

    @Nested
    class AcceptsOptionalGaps {

        @Test
        void parsesADocumentThatOmitsOptionalInfoFields() {
            assertTrue(parse(candidateOmittingOptionalInfo.get()).isSuccess());
        }

        @Test
        void parsesADocumentThatOmitsOptionalOnboardingExtensions() {
            assertTrue(parse(candidateOmittingOptionalMetadata.get()).isSuccess());
        }
    }

    @Nested
    class RejectsUnusableInput {

        @Test
        void reportsMissingDataWhenTheStateCarriesNoSource() {
            Try<State> result = parser().process(Try.success(new State()));

            assertEquals(MISSING_DATA, onlyRejection(result));
        }

        @Test
        void reportsProcessingFailedWhenNoReaderCanResolveTheSource() {
            assertEquals(PROCESSING_FAILED, onlyRejection(parse(unsupportedSchemeSource.get())));
        }

        @Test
        void reportsProcessingFailedWhenTheSourceCannotBeRead() {
            assertEquals(PROCESSING_FAILED, onlyRejection(parse(unreadableSource.get())));
        }
    }

    @Nested
    class HonoursIncomingFailures {

        @Test
        void passesAnAlreadyFailedStateThroughUnparsed() {
            Exception upstream = new IllegalStateException("upstream failed");

            Try<State> result = parser().process(Try.failure(upstream));

            assertTrue(result.isFailure());
            assertTrue(result.getExceptions().contains(upstream));
        }
    }
}
