package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.out.ScorerDelegate;
import io.obya.api.onboarding.domain.model.Status;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.Scorecard;
import io.obya.common.util.Try;

import static io.obya.api.onboarding.domain.model.Violation.Code.*;
import static io.obya.api.onboarding.appl.usecase.processing.Validator.nonNull;

public class Scorer implements Processor<State> {

    private final ScorerDelegate delegate;

    public Scorer(ScorerDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public Try<State> process(Try<State> state) {
        return state
            .filter(st -> nonNull(st::source) || nonNull(st::body), MISSING_DATA.failure( "state.source"), true)
            .filter(st -> nonNull(st::contract), MISSING_DATA.failure( "state.contract"), true)
            .flatMap(st -> {
                final Try<Scorecard> scored = nonNull(st::source) ?
                        delegate.score(st.source(), st.contract().type()) :
                        delegate.score(st.body().get(), st.contract().type());

                if (scored.isFailure()) {
                    return scored.recoverWithOther(e ->
                            new Try.Partial<>(st.score(Scorecard.undefined()), e));
                }
                return scored
                        .map(sc -> st.score(sc).status(Status.SCORED))
                        .filter(s -> !s.score().isTooLow(), s -> INSUFFICIENT_SCORING.failure(s.score()).get(), true);
            });
    }
}
