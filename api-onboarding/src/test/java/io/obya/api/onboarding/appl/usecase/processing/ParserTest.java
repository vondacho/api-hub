package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.Contract;
import io.obya.api.onboarding.domain.model.Status;
import io.obya.api.onboarding.domain.model.Violation;
import io.obya.common.util.Try;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static io.obya.api.onboarding.domain.model.DomainExamples.Contracts.asyncApiV30;
import static io.obya.api.onboarding.domain.model.DomainExamples.Contracts.openApiV30;
import static io.obya.api.onboarding.domain.model.Violation.Code.MISSING_DATA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Behavioural specification of the {@link Parser} processor.
 *
 * <p>The Parser is the dispatcher of the parsing step: it reads the contract version carried by
 * the workflow {@link State}, hands the state to the parse strategy registered for that version,
 * and records the outcome:</p>
 * <ul>
 *   <li>the strategy registered for the state's contract version — and only that one — is run;</li>
 *   <li>a successfully parsed state is marked {@link Status#VALID};</li>
 *   <li>the strategy's outcome — success, partial with violations, or failure — flows through;</li>
 *   <li>a state without a contract is rejected with {@code MISSING_DATA}, unparsed;</li>
 *   <li>a contract version with no registered strategy is rejected rather than silently accepted;</li>
 *   <li>an already-failed incoming state is passed through untouched, without parsing.</li>
 * </ul>
 *
 * <p>Tests assert on the resulting {@code Try<State>} — its success/partial/failure shape, the
 * resulting status and violation codes — not on how a strategy is driven. Strategies are Mockito
 * mocks; to keep dispatching observable, a strategy stamps its own tag into the state body.</p>
 */
class ParserTest {

    private static final String OPENAPI_TAG = "parsed-by:openapi-v30";
    private static final String ASYNCAPI_TAG = "parsed-by:asyncapi-v30";

    @SuppressWarnings("unchecked")
    private static Processor<State> strategy() {
        return mock(Processor.class);
    }

    // A strategy that "parses" by stamping its tag into the body, making it observable
    // which strategy the Parser dispatched to.
    private static Processor<State> strategyTagging(String tag) {
        Processor<State> strategy = strategy();
        when(strategy.process(any())).thenAnswer(invocation -> {
            Try<State> in = invocation.getArgument(0);
            return in.map(s -> s.body(() -> tag));
        });
        return strategy;
    }

    private static Map<Contract.Version, Supplier<Processor<State>>> registeredFor(
            Contract.Version version, Processor<State> strategy) {
        return Map.of(version, () -> strategy);
    }

    private static Try<State> parse(Map<Contract.Version, Supplier<Processor<State>>> strategies, State state) {
        return new Parser(strategies).process(Try.success(state));
    }

    private static Violation.Failure onlyFailure(Try<State> result) {
        assertTrue(result.isFailure(), () -> "expected a failure but was: " + result);
        assertEquals(1, result.getExceptions().size(), "expected exactly one violation");
        Exception e = result.getExceptions().getFirst();
        assertInstanceOf(Violation.Failure.class, e);
        return (Violation.Failure) e;
    }

    @Nested
    class DispatchesOnTheContractVersion {

        @Test
        void runsTheStrategyRegisteredForTheContractVersion() {
            Processor<State> openApi = strategyTagging(OPENAPI_TAG);

            Try<State> result = parse(registeredFor(Contract.Version.OPENAPI_V30, openApi),
                    new State().contract(openApiV30.get()));

            assertTrue(result.isSuccess());
            assertEquals(OPENAPI_TAG, result.getValue().orElseThrow().body().get());
        }

        @Test
        void leavesTheStrategiesOfOtherContractVersionsUntouched() {
            Processor<State> openApi = strategyTagging(OPENAPI_TAG);
            Processor<State> asyncApi = strategyTagging(ASYNCAPI_TAG);

            Try<State> result = parse(
                    Map.of(Contract.Version.OPENAPI_V30, () -> openApi,
                           Contract.Version.ASYNCAPI_V30, () -> asyncApi),
                    new State().contract(asyncApiV30.get()));

            assertEquals(ASYNCAPI_TAG, result.getValue().orElseThrow().body().get());
            verify(openApi, never()).process(any());
        }
    }

    @Nested
    class MarksAParsedStateValid {

        @Test
        void marksTheStateValidWhenParsingSucceeds() {
            Processor<State> openApi = strategyTagging(OPENAPI_TAG);

            Try<State> result = parse(registeredFor(Contract.Version.OPENAPI_V30, openApi),
                    new State().contract(openApiV30.get()));

            assertEquals(Status.VALID, result.getValue().orElseThrow().status());
        }
    }

    @Nested
    class PropagatesTheStrategyOutcome {

        @Test
        void keepsPartialViolationsWhileStillMarkingTheStateValid() {
            Exception violation = new IllegalStateException("specification partially parsed");
            Processor<State> openApi = strategy();
            when(openApi.process(any())).thenAnswer(invocation -> {
                Try<State> in = invocation.getArgument(0);
                return new Try.Partial<>(in.getValue().orElseThrow(), List.of(violation));
            });

            Try<State> result = parse(registeredFor(Contract.Version.OPENAPI_V30, openApi),
                    new State().contract(openApiV30.get()));

            assertTrue(result.isPartial(), () -> "expected a partial result but was: " + result);
            assertEquals(Status.VALID, result.getValue().orElseThrow().status());
            assertTrue(result.getExceptions().contains(violation));
        }

        @Test
        void propagatesAStrategyFailureWithoutMarkingTheStateValid() {
            Exception violation = new IllegalStateException("specification could not be parsed");
            Processor<State> openApi = strategy();
            when(openApi.process(any())).thenReturn(Try.failure(violation));

            Try<State> result = parse(registeredFor(Contract.Version.OPENAPI_V30, openApi),
                    new State().contract(openApiV30.get()));

            assertTrue(result.isFailure(), () -> "expected a failure but was: " + result);
            assertTrue(result.getExceptions().contains(violation));
        }
    }

    @Nested
    class RejectsUnusableInput {

        @Test
        void reportsMissingDataWhenTheContractIsAbsent() {
            Processor<State> openApi = strategy();

            Try<State> result = parse(registeredFor(Contract.Version.OPENAPI_V30, openApi), new State());

            Violation.Failure failure = onlyFailure(result);
            assertEquals(MISSING_DATA, failure.code);
            assertTrue(failure.getMessage().contains("state.contract"),
                    () -> "violation should point at the missing contract but was: " + failure.getMessage());
            verifyNoInteractions(openApi);
        }

        @Test
        void rejectsAContractVersionThatHasNoRegisteredStrategy() {
            Processor<State> openApi = strategyTagging(OPENAPI_TAG);

            Try<State> result = parse(registeredFor(Contract.Version.OPENAPI_V30, openApi),
                    new State().contract(asyncApiV30.get()));

            assertTrue(result.isFailure(), () -> "expected a failure but was: " + result);
            verify(openApi, never()).process(any());
        }
    }

    @Nested
    class HonoursIncomingFailures {

        @Test
        void passesAnAlreadyFailedStateThroughWithoutParsing() {
            Processor<State> openApi = strategy();
            Try<State> incoming = Try.failure(new IllegalStateException("upstream failed"));

            Try<State> result = new Parser(registeredFor(Contract.Version.OPENAPI_V30, openApi)).process(incoming);

            assertTrue(result.isFailure());
            verifyNoInteractions(openApi);
        }
    }
}
