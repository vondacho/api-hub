package io.obya.api.onboarding.appl.usecase.processing.aas;

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

import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Candidates.Aas.apiBundleName;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Candidates.Aas.apiName;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Candidates.Aas.apiProductName;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.Aas.*;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.unreadableSource;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.unsupportedSchemeSource;
import static io.obya.api.onboarding.domain.model.Version.V1;
import static io.obya.api.onboarding.domain.model.Violation.Code.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioural specification of the {@link AASParser} template, exercised through its
 * {@link AASV30Parser} incarnation.
 *
 * <p>The parser turns a source URI into a populated workflow {@link State}: it reads the document,
 * lifts the AsyncAPI {@code info} block into {@link Info}, lifts the {@code x-} onboarding
 * extensions into {@link Metadata}, and carries the document into the state body. What it refuses
 * is as much part of its contract as what it produces:</p>
 * <ul>
 *   <li>an info field or onboarding extension that is required but empty is rejected with
 *       {@code MISSING_DATA};</li>
 *   <li>an API version that is not {@code vN} is rejected with {@code MALFORMED_VERSION}, and a
 *       revision that is not semver with {@code MALFORMED_REVISION};</li>
 *   <li>a document that cannot be bound to the AsyncAPI model, a source that no reader can
 *       resolve, and a source that cannot be read are all rejected with
 *       {@code PROCESSING_FAILED};</li>
 *   <li>a state carrying no source at all is rejected with {@code MISSING_DATA};</li>
 *   <li>an already-failed incoming state is passed through untouched, unparsed.</li>
 * </ul>
 *
 * <p>Two things distinguish this parser from its OpenAPI sibling, and both are specified below:
 * the body keeps the <em>submitted document verbatim</em> rather than a re-serialised rendering,
 * and every parsing problem collapses into {@code PROCESSING_FAILED} because binding is the only
 * parsing step. The revision is deliberately not defaulted — a document that declares none leaves
 * it unset for the {@code Revisor} to assign.</p>
 *
 * <p>Tests assert on the resulting {@code Try<State>}. Nothing is mocked: the documents are the
 * real example specifications, addressed on the classpath so they resolve on any machine.</p>
 */
class AASParserTest {

    private static Processor<State> parser() {
        return new AASV30Parser(new ClasspathResourceReader(), new URIFileReader());
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

            assertEquals("Notification API", info.title());
            assertEquals("A sample AsyncAPI 3.0 spec for end-to-end test fixtures.", info.description());
            assertEquals(V1, info.version());
        }

        @Test
        void liftsTheOnboardingExtensionsIntoTheMetadata() {
            Metadata metadata = parsed(parse(validCandidate.get())).metadata();

            assertEquals(apiName, metadata.name());
            assertEquals(apiBundleName, metadata.bundleName());
            assertEquals(apiProductName, metadata.productName());
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
        void keepsTheSubmittedDocumentVerbatimInTheBody() {
            String body = parsed(parse(validCandidate.get())).body().get();

            assertTrue(body.startsWith("asyncapi: 3.0.0"),
                    () -> "the body should be the submitted document, not a rendering of it, but was: " + body);
            assertTrue(body.contains("address: platform/notifications"),
                    () -> "the body should carry the whole document but was: " + body);
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
    class RejectsUnusableInput {

        @Test
        void reportsMissingDataWhenTheStateCarriesNoSource() {
            Try<State> result = parser().process(Try.success(new State()));

            assertEquals(MISSING_DATA, onlyRejection(result));
        }

        @Test
        void reportsProcessingFailedWhenTheDocumentCannotBeBoundToTheModel() {
            assertEquals(PROCESSING_FAILED, onlyRejection(parse(unparseableCandidate.get())));
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
