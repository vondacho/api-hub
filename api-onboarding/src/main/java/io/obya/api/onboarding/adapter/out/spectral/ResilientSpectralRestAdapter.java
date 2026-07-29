package io.obya.api.onboarding.adapter.out.spectral;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.obya.api.onboarding.adapter.out.scorer.api.ScoringApi;
import io.obya.api.onboarding.domain.model.Contract;
import io.obya.api.onboarding.domain.model.Scorecard;
import io.obya.common.util.Try;

import java.net.URI;

public class ResilientSpectralRestAdapter extends SpectralRestAdapter {

    public ResilientSpectralRestAdapter(ScoringApi scoringApi) {
        super(scoringApi);
    }

    @CircuitBreaker(name = "spectral", fallbackMethod = "registerFallback")
    @Bulkhead(name = "spectral")
    @Retry(name = "spectral")
    @Override
    public Try<Scorecard> score(URI source, Contract.Type contract) {
        return super.score(source, contract);
    }

    @CircuitBreaker(name = "spectral", fallbackMethod = "registerFallback")
    @Bulkhead(name = "spectral")
    @Retry(name = "spectral")
    @Override
    public Try<Scorecard> score(String source, Contract.Type contract) {
        return super.score(source, contract);
    }
}
