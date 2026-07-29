package io.obya.api.onboarding.adapter.out.jentic;

import io.obya.api.onboarding.appl.out.ScorerDelegate;
import io.obya.api.onboarding.domain.model.Contract;
import io.obya.api.onboarding.domain.model.Score;
import io.obya.api.onboarding.domain.model.Scorecard;
import io.obya.common.util.Try;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

import static io.obya.api.onboarding.domain.model.Scorecard.Dimension.*;

@ConditionalOnProperty(name = "scorer.adapter", havingValue = "jentic")
@Component(value = "jentic")
public class JenticRestAdapter implements ScorerDelegate {

    @Override
    public Try<Scorecard> score(URI source, Contract.Type contract) {
        return Try.success(new Scorecard(
                new Score(74),
                Map.of(
                        FC, new Score(99),
                        SEC, new Score(100),
                        DX, new Score(66),
                        ARAX, new Score(51),
                        AU, new Score(93),
                        AID, new Score(100)
                )
        ));
    }

    @Override
    public Try<Scorecard> score(String source, Contract.Type contract) {
        return Try.success(new Scorecard(
                new Score(71),
                Map.of(
                        FC, new Score(99),
                        SEC, new Score(100),
                        DX, new Score(66),
                        ARAX, new Score(51),
                        AU, new Score(93),
                        AID, new Score(100)
                )
        ));
    }
}
