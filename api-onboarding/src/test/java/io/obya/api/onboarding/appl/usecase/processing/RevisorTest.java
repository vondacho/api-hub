package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.out.Registry;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.Metadata;
import io.obya.api.onboarding.domain.model.Revision;
import io.obya.api.onboarding.domain.model.Specification;
import io.obya.api.onboarding.domain.model.Version;
import io.obya.api.onboarding.domain.model.Violation;
import io.obya.common.util.Try;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Candidates.candidateOf;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Candidates.name;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Candidates.productName;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Registries.nothingRegistered;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Registries.registeredAt;
import static io.obya.api.onboarding.domain.model.DomainExamples.Components.petstoreQuarkus;
import static io.obya.api.onboarding.domain.model.Version.V1;
import static io.obya.api.onboarding.domain.model.Violation.Code.MISSING_DATA;
import static io.obya.api.onboarding.domain.model.Violation.Code.REVISION_AUTO_INCREMENTED;
import static io.obya.api.onboarding.domain.model.Violation.Code.REVISION_NOT_ALIGNED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Behavioural specification of the {@link Revisor} processor.
 *
 * <p>The Revisor decides which revision a candidate will be registered under. It reconciles the
 * revision the candidate declares with the latest revision the {@link Registry} already holds for
 * the same name, product and major version:</p>
 * <ul>
 *   <li>nothing registered yet — the candidate opens the series at the version's base revision,
 *       and the registry miss is not itself reported as a violation;</li>
 *   <li>something registered already — the candidate must come strictly after it, otherwise the
 *       revision is auto-incremented to the next patch and {@code REVISION_AUTO_INCREMENTED}
 *       is raised;</li>
 *   <li>a declared revision that contradicts the declared version is realigned, raising
 *       {@code REVISION_NOT_ALIGNED};</li>
 *   <li>a declared revision that is both aligned and ahead is honoured as-is;</li>
 *   <li>whichever revision is assigned, the component association is reset — a new revision
 *       always requires a fresh implementation;</li>
 *   <li>a candidate without info or metadata is rejected with {@code MISSING_DATA};</li>
 *   <li>an already-failed incoming state is passed through untouched.</li>
 * </ul>
 *
 * <p>Tests assert on the resulting {@code Try<State>} — the revision and component carried by its
 * metadata, its success/partial/failure shape and the violation codes raised. The Revisor's
 * traffic with the registry is specified separately, in {@code ConsultsTheRegistry}: it is a
 * read-only, single-lookup collaboration keyed on the candidate's identity, and it must not
 * happen at all when the candidate is unusable.</p>
 */
class RevisorTest {

    private static Registry registryHolding(Try<Specification> latest) {
        Registry registry = mock(Registry.class);
        when(registry.at(anyString(), anyString(), any(Version.class))).thenReturn(latest);
        return registry;
    }

    private static Try<State> revise(Registry registry, State candidate) {
        return new Revisor(registry).process(Try.success(candidate));
    }

    private static Metadata assignedMetadata(Try<State> result) {
        return result.getValue().orElseThrow().metadata();
    }

    private static Revision assignedRevision(Try<State> result) {
        return assignedMetadata(result).revision();
    }

    private static Violation.Failure onlyViolation(Try<State> result) {
        assertEquals(1, result.getExceptions().size(),
                () -> "expected exactly one violation but was: " + result.getExceptions());
        Exception e = result.getExceptions().getFirst();
        assertInstanceOf(Violation.Failure.class, e);
        return (Violation.Failure) e;
    }

    private static Violation.Code onlyWarning(Try<State> result) {
        assertTrue(result.isPartial(), () -> "expected a partial result but was: " + result);
        return onlyViolation(result).code;
    }

    private static Violation.Code onlyRejection(Try<State> result) {
        assertTrue(result.isFailure(), () -> "expected a failure but was: " + result);
        return onlyViolation(result).code;
    }

    @Nested
    class OpensTheSeriesWhenNothingIsRegistered {

        @Test
        void assignsTheVersionBaseRevisionWhenTheCandidateDeclaresNone() {
            Try<State> result = revise(registryHolding(nothingRegistered.get()), candidateOf(V1, null));

            assertTrue(result.isSuccess(), () -> "expected a success but was: " + result);
            assertEquals(Revision.V100, assignedRevision(result));
        }

        @Test
        void doesNotReportTheAbsenceOfAPriorRevisionAsAViolation() {
            Try<State> result = revise(registryHolding(nothingRegistered.get()), candidateOf(V1, null));

            assertFalse(result.hasExceptions(),
                    () -> "a first revision should raise no violation but got: " + result.getExceptions());
        }

        @Test
        void honoursADeclaredRevisionThatMatchesTheVersion() {
            Try<State> result = revise(registryHolding(nothingRegistered.get()), candidateOf(V1, Revision.V101));

            assertTrue(result.isSuccess(), () -> "expected a success but was: " + result);
            assertEquals(Revision.V101, assignedRevision(result));
        }
    }

    @Nested
    class SupersedesTheRegisteredRevision {

        @Test
        void assignsTheNextPatchWhenTheCandidateDeclaresNoRevision() {
            Try<State> result = revise(registryHolding(registeredAt(Revision.V100)), candidateOf(V1, null));

            assertTrue(result.isSuccess(), () -> "expected a success but was: " + result);
            assertEquals(Revision.V101, assignedRevision(result));
        }

        @Test
        void honoursADeclaredRevisionThatIsAheadOfTheRegisteredOne() {
            Try<State> result = revise(registryHolding(registeredAt(Revision.V100)), candidateOf(V1, Revision.V101));

            assertTrue(result.isSuccess(), () -> "expected a success but was: " + result);
            assertEquals(Revision.V101, assignedRevision(result));
        }

        @Test
        void autoIncrementsADeclaredRevisionThatRepeatsTheRegisteredOne() {
            Try<State> result = revise(registryHolding(registeredAt(Revision.V100)), candidateOf(V1, Revision.V100));

            assertEquals(REVISION_AUTO_INCREMENTED, onlyWarning(result));
            assertEquals(Revision.V101, assignedRevision(result));
        }

        @Test
        void autoIncrementsADeclaredRevisionThatTrailsTheRegisteredOne() {
            Try<State> result = revise(registryHolding(registeredAt(Revision.V101)), candidateOf(V1, Revision.V100));

            assertEquals(REVISION_AUTO_INCREMENTED, onlyWarning(result));
            assertEquals(Revision.from("1.0.2"), assignedRevision(result));
        }
    }

    @Nested
    class RealignsARevisionThatContradictsTheVersion {

        @Test
        void fallsBackToTheVersionBaseRevisionWhenNothingIsRegistered() {
            Try<State> result = revise(registryHolding(nothingRegistered.get()), candidateOf(V1, Revision.V200));

            assertEquals(REVISION_NOT_ALIGNED, onlyWarning(result));
            assertEquals(Revision.V100, assignedRevision(result));
        }

        @Test
        void fallsBackToTheNextPatchOfTheRegisteredRevision() {
            Try<State> result = revise(registryHolding(registeredAt(Revision.V100)), candidateOf(V1, Revision.V200));

            assertEquals(REVISION_NOT_ALIGNED, onlyWarning(result));
            assertEquals(Revision.V101, assignedRevision(result));
        }
    }

    @Nested
    class RequiresAFreshImplementationForANewRevision {

        @Test
        void clearsTheComponentAssociationWhenTheRevisionIsAutoIncremented() {
            Try<State> result = revise(registryHolding(registeredAt(Revision.V100)),
                    candidateOf(V1, Revision.V100, petstoreQuarkus.get()));

            assertEquals(Revision.V101, assignedRevision(result));
            assertNull(assignedMetadata(result).componentName());
            assertNull(assignedMetadata(result).componentRevision());
        }

        @Test
        void clearsTheComponentAssociationWhenTheRevisionIsRealigned() {
            Try<State> result = revise(registryHolding(nothingRegistered.get()),
                    candidateOf(V1, Revision.V200, petstoreQuarkus.get()));

            assertEquals(Revision.V100, assignedRevision(result));
            assertNull(assignedMetadata(result).componentName());
            assertNull(assignedMetadata(result).componentRevision());
        }

        @Test
        void clearsTheComponentAssociationWhenTheDeclaredRevisionIsHonoured() {
            Try<State> result = revise(registryHolding(registeredAt(Revision.V100)),
                    candidateOf(V1, Revision.V101, petstoreQuarkus.get()));

            assertEquals(Revision.V101, assignedRevision(result));
            assertNull(assignedMetadata(result).componentName());
            assertNull(assignedMetadata(result).componentRevision());
        }
    }

    @Nested
    class ConsultsTheRegistry {

        @Test
        void asksOnlyForTheLatestRevisionOfTheCandidateNameProductAndVersion() {
            Registry registry = registryHolding(registeredAt(Revision.V100));

            revise(registry, candidateOf(V1, null));

            verify(registry).at(name, productName, V1);
            verifyNoMoreInteractions(registry);
        }

        @Test
        void neverWritesToTheRegistry() {
            Registry registry = registryHolding(registeredAt(Revision.V100));

            revise(registry, candidateOf(V1, Revision.V100));

            verify(registry, never()).register(any());
        }

        @Test
        void isNotConsultedWhenTheCandidateIsUnusable() {
            Registry registry = mock(Registry.class);

            revise(registry, candidateOf(V1, null).metadata(null));

            verifyNoInteractions(registry);
        }

        @Test
        void isNotConsultedWhenTheIncomingStateAlreadyFailed() {
            Registry registry = mock(Registry.class);

            new Revisor(registry).process(Try.failure(new IllegalStateException("upstream failed")));

            verifyNoInteractions(registry);
        }
    }

    @Nested
    class RejectsUnusableInput {

        @Test
        void reportsMissingDataWhenTheInfoIsAbsent() {
            Try<State> result = revise(mock(Registry.class), candidateOf(V1, null).info(null));

            assertEquals(MISSING_DATA, onlyRejection(result));
        }

        @Test
        void reportsMissingDataWhenTheMetadataIsAbsent() {
            Try<State> result = revise(mock(Registry.class), candidateOf(V1, null).metadata(null));

            assertEquals(MISSING_DATA, onlyRejection(result));
        }
    }

    @Nested
    class HonoursIncomingFailures {

        @Test
        void passesAnAlreadyFailedStateThroughUnrevised() {
            Exception upstream = new IllegalStateException("upstream failed");

            Try<State> result = new Revisor(mock(Registry.class)).process(Try.failure(upstream));

            assertTrue(result.isFailure());
            assertTrue(result.getExceptions().contains(upstream));
        }
    }
}
