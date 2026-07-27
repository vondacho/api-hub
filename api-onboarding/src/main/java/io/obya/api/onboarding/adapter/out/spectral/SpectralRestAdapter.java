package io.obya.api.onboarding.adapter.out.spectral;

import io.obya.api.onboarding.adapter.out.scorer.api.ScoringApi;
import io.obya.api.onboarding.adapter.out.scorer.model.Candidate;
import io.obya.api.onboarding.adapter.out.scorer.model.CandidateProcessed;
import io.obya.api.onboarding.adapter.out.utilities.HttpExchange;
import io.obya.api.onboarding.appl.out.ScorerDelegate;
import io.obya.api.onboarding.domain.model.Contract;
import io.obya.api.onboarding.domain.model.Score;
import io.obya.api.onboarding.domain.model.Scorecard;
import io.obya.common.util.Try;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;

@ConditionalOnProperty(name = "scorer.adapter", havingValue = "spectral")
@Component(value = "spectral")
public class SpectralRestAdapter implements ScorerDelegate {

    private final ScoringApi scoringApi;

    public SpectralRestAdapter(ScoringApi scoringApi) {
        this.scoringApi = scoringApi;
    }

    @Override
    public Try<Scorecard> score(URI source, Contract contract) {
        return HttpExchange.execute(() -> scoringApi.scoreCandidate(new Candidate(source)),
                body -> Try.success(from(body)));
    }

    @Override
    public Try<Scorecard> score(String source, Contract contract) {
        return score(URI.create(source), contract);
    }

    private Scorecard from(CandidateProcessed data) {
        var dimensions = new HashMap<Scorecard.Dimension, Score>();
        data.getScorecard().getDimensions().forEach(it ->
                dimensions.put(
                        Scorecard.Dimension.valueOf(it.getName()),
                        new Score(it.getScore().intValue())));
        return new Scorecard(new Score(data.getScorecard().getScore().intValue()), dimensions);
    }
}
