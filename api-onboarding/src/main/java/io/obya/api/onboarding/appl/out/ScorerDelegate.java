package io.obya.api.onboarding.appl.out;

import io.obya.common.util.Try;
import io.obya.api.onboarding.domain.model.Contract;
import io.obya.api.onboarding.domain.model.Scorecard;

import java.net.URI;

public interface ScorerDelegate {

    Try<Scorecard> score(URI source, Contract.Type contract);
    Try<Scorecard> score(String source, Contract.Type contract);
}
