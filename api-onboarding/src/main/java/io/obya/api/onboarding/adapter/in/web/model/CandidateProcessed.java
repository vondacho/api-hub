package io.obya.api.onboarding.adapter.in.web.model;

import io.obya.api.onboarding.domain.model.Status;
import io.obya.api.onboarding.domain.model.Violation;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.Contract;
import io.obya.api.onboarding.domain.model.Info;
import io.obya.api.onboarding.domain.model.Metadata;

import java.util.List;

public record CandidateProcessed(
        String id,
        String source,
        Info info,
        Metadata metadata,
        Contract.Version contract,
        Status status,
        ScoreSummary scorecard,
        List<Violation> violations
) {
    public static CandidateProcessed from(State state, List<Violation> violations) {
        return new CandidateProcessed(
                state.status() == Status.REGISTERED ? state.id().id() : null,
                state.source() == null ? null : state.source().toString(),
                state.info(),
                state.metadata(),
                state.contract().version(),
                state.status(),
                state.score().isUndefined() ? null : ScoreSummary.from(state.score()),
                violations.isEmpty() ? null : violations.stream().sorted().toList());
    }
}
