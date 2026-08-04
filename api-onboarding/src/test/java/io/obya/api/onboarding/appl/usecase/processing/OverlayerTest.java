package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.usecase.UsecaseExamples;
import io.obya.api.onboarding.appl.usecase.processing.oai.OverlayParser;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.common.util.Try;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Behavioural specification of the {@link Overlayer} processor.
 *
 * <p>The Overlayer applies a configured overlay document to a candidate state by handing the work
 * to an {@link OverlayParser} strategy. Its own contract is narrow:</p>
 * <ul>
 *   <li>the strategy is run against the configured overlay URI (that overlay, and no other);</li>
 *   <li>whatever the overlay changed, the state's original source URI is restored afterwards;</li>
 *   <li>the strategy's outcome — success, partial with violations, or failure — flows through;</li>
 *   <li>an already-failed incoming state is passed through untouched, without overlaying.</li>
 * </ul>
 *
 * <p>Tests assert on the resulting {@code Try<State>} rather than on how the strategy is driven.
 * The {@link OverlayParser} strategy is a Mockito mock whose behaviour is scripted per scenario;
 * to keep the assertions observable it records the source it was given into the state body.</p>
 */
class OverlayerTest {

    private static final URI ORIGINAL_SOURCE = UsecaseExamples.Sources.Oas.validCandidate.get();
    private static final URI OVERLAY = UsecaseExamples.Sources.Oai.validOverlay.get();

    // A strategy that "applies" the overlay by writing the source it received into the body,
    // making it observable which URI the Overlayer handed over.
    private static OverlayParser strategyRecordingItsSource() {
        OverlayParser strategy = mock(OverlayParser.class);
        when(strategy.process(any())).thenAnswer(invocation -> {
            Try<State> in = invocation.getArgument(0);
            return in.map(s -> new State()
                    .source(s.source())
                    .body(() -> "overlaid-from:" + s.source()));
        });
        return strategy;
    }

    private static Try<State> overlay(OverlayParser strategy, State state) {
        return new Overlayer(OVERLAY, strategy).process(Try.success(state));
    }

    @Nested
    class AppliesTheConfiguredOverlay {

        @Test
        void handsTheConfiguredOverlayToTheStrategy() {
            Try<State> result = overlay(strategyRecordingItsSource(), new State().source(ORIGINAL_SOURCE));

            assertTrue(result.isSuccess());
            assertEquals("overlaid-from:" + OVERLAY, result.getValue().orElseThrow().body().get());
        }

        @Test
        void restoresTheOriginalSourceAfterOverlaying() {
            Try<State> result = overlay(strategyRecordingItsSource(), new State().source(ORIGINAL_SOURCE));

            assertEquals(ORIGINAL_SOURCE, result.getValue().orElseThrow().source());
        }
    }

    @Nested
    class PropagatesTheStrategyOutcome {

        @Test
        void keepsPartialViolationsWhileRestoringTheSource() {
            Exception violation = new IllegalStateException("overlay partially applied");
            OverlayParser strategy = mock(OverlayParser.class);
            when(strategy.process(any())).thenAnswer(invocation -> {
                Try<State> in = invocation.getArgument(0);
                return new Try.Partial<>(in.getValue().orElseThrow(), List.of(violation));
            });

            Try<State> result = overlay(strategy, new State().source(ORIGINAL_SOURCE));

            assertTrue(result.isPartial(), () -> "expected a partial result but was: " + result);
            assertEquals(ORIGINAL_SOURCE, result.getValue().orElseThrow().source());
            assertTrue(result.getExceptions().contains(violation));
        }

        @Test
        void propagatesAStrategyFailure() {
            Exception violation = new IllegalStateException("overlay could not be applied");
            OverlayParser strategy = mock(OverlayParser.class);
            when(strategy.process(any())).thenReturn(Try.failure(violation));

            Try<State> result = overlay(strategy, new State().source(ORIGINAL_SOURCE));

            assertTrue(result.isFailure());
            assertTrue(result.getExceptions().contains(violation));
        }
    }

    @Nested
    class HonoursIncomingFailures {

        @Test
        void passesAnAlreadyFailedStateThroughWithoutOverlaying() {
            OverlayParser strategy = mock(OverlayParser.class);
            Try<State> incoming = Try.failure(new IllegalStateException("upstream failed"));

            Try<State> result = new Overlayer(OVERLAY, strategy).process(incoming);

            assertTrue(result.isFailure());
            verify(strategy, never()).process(any());
        }
    }
}
