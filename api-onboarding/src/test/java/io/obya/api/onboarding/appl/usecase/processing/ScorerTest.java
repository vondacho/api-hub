package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.out.ScorerDelegate;
import io.obya.api.onboarding.appl.usecase.UsecaseExamples;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.Status;
import io.obya.api.onboarding.domain.model.Violation;
import io.obya.common.util.Try;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.obya.api.onboarding.domain.model.DomainExamples.Contracts.openApiV30;
import static io.obya.api.onboarding.domain.model.DomainExamples.Scores.acceptable;
import static io.obya.api.onboarding.domain.model.DomainExamples.Scores.acceptableEvaluation;
import static io.obya.api.onboarding.domain.model.DomainExamples.Scores.tooLow;
import static io.obya.api.onboarding.domain.model.Violation.Code.INSUFFICIENT_SCORING;
import static io.obya.api.onboarding.domain.model.Violation.Code.MISSING_DATA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Behavioural specification of the {@link Scorer} processor.
 *
 * <p>The Scorer takes a workflow {@link State} that carries a contract plus either a source URI
 * or an inline body, asks the {@link ScorerDelegate} to grade it, and records the outcome on the
 * state:</p>
 * <ul>
 *   <li>an acceptable score is attached and the state is marked {@link Status#SCORED};</li>
 *   <li>a score that is too low is rejected with an {@code INSUFFICIENT_SCORING} violation;</li>
 *   <li>a scoring engine failure degrades gracefully — the state keeps flowing with an
 *       undefined scorecard while the failure is preserved for observability;</li>
 *   <li>missing input (no source/body, or no contract) is rejected with {@code MISSING_DATA}.</li>
 * </ul>
 *
 * <p>Tests assert on the resulting {@code Try<State>} — its success/partial/failure shape, the
 * attached scorecard and status, and the violation codes — not on how the delegate is called.
 * The {@link ScorerDelegate} is a Mockito mock configured per scenario.</p>
 */
class ScorerTest {

    private static Try<State> score(ScorerDelegate delegate, State state) {
        return new Scorer(delegate).process(Try.success(state));
    }

    private static Violation.Failure onlyFailure(Try<State> result) {
        assertTrue(result.isFailure(), () -> "expected a failure but was: " + result);
        assertEquals(1, result.getExceptions().size(), "expected exactly one violation");
        Exception e = result.getExceptions().getFirst();
        assertInstanceOf(Violation.Failure.class, e);
        return (Violation.Failure) e;
    }

    @Nested
    class GradesTheSpecification {

        @Test
        void attachesAnAcceptableScoreAndMarksTheStateScored() {
            ScorerDelegate delegate = mock(ScorerDelegate.class);
            when(delegate.score(any(URI.class), any())).thenReturn(Try.success(acceptable.get()));

            Try<State> result = score(delegate, new State()
                    .source(UsecaseExamples.Sources.Oas.validCandidate.get())
                    .contract(openApiV30.get()));

            assertTrue(result.isSuccess());
            State scored = result.getValue().orElseThrow();
            assertEquals(acceptableEvaluation, scored.score().global().evaluation());
            assertEquals(Status.SCORED, scored.status());
        }

        @Test
        void gradesTheInlineBodyWhenNoSourceIsPresent() {
            ScorerDelegate delegate = mock(ScorerDelegate.class);
            when(delegate.score(any(String.class), any())).thenReturn(Try.success(acceptable.get()));

            Try<State> result = score(delegate, new State()
                    .body(() -> "openapi: 3.0.3")
                    .contract(openApiV30.get()));

            assertTrue(result.isSuccess());
            assertEquals(Status.SCORED, result.getValue().orElseThrow().status());
        }
    }

    @Nested
    class RejectsInsufficientScore {

        @Test
        void reportsInsufficientScoringWhenTheScoreIsTooLow() {
            ScorerDelegate delegate = mock(ScorerDelegate.class);
            when(delegate.score(any(URI.class), any())).thenReturn(Try.success(tooLow.get()));

            Try<State> result = score(delegate, new State()
                    .source(UsecaseExamples.Sources.Oas.validCandidate.get())
                    .contract(openApiV30.get()));

            assertEquals(INSUFFICIENT_SCORING, onlyFailure(result).code);
        }
    }

    @Nested
    class DegradesGracefullyOnEngineFailure {

        @Test
        void keepsTheStateFlowingWithAnUndefinedScoreAndPreservesTheFailure() {
            ScorerDelegate delegate = mock(ScorerDelegate.class);
            when(delegate.score(any(URI.class), any()))
                    .thenReturn(Try.failure(new IllegalStateException("scoring engine unavailable")));

            Try<State> result = score(delegate, new State()
                    .source(UsecaseExamples.Sources.Oas.validCandidate.get())
                    .contract(openApiV30.get()));

            assertTrue(result.isPartial(), () -> "expected a partial result but was: " + result);
            assertTrue(result.getValue().orElseThrow().score().isUndefined());
            assertTrue(result.hasExceptions());
        }
    }

    @Nested
    class RejectsUnusableInput {

        @Test
        void reportsMissingDataWhenNeitherSourceNorBodyIsPresent() {
            ScorerDelegate delegate = mock(ScorerDelegate.class);

            Try<State> result = score(delegate, new State().contract(openApiV30.get()));

            assertEquals(MISSING_DATA, onlyFailure(result).code);
            verifyNoInteractions(delegate);
        }

        @Test
        void reportsMissingDataWhenTheContractIsAbsent() {
            ScorerDelegate delegate = mock(ScorerDelegate.class);

            Try<State> result = score(delegate, new State().source(UsecaseExamples.Sources.Oas.validCandidate.get()));

            Violation.Failure failure = onlyFailure(result);
            assertEquals(MISSING_DATA, failure.code);
            assertTrue(failure.getMessage().contains("state.contract"),
                    () -> "violation should point at the missing contract but was: " + failure.getMessage());
            verifyNoInteractions(delegate);
        }
    }
}
