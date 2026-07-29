package io.obya.api.onboarding.adapter.out.config;

import io.obya.api.onboarding.appl.out.ScorerDelegate;
import io.obya.api.onboarding.domain.model.*;
import io.obya.common.util.Try;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Map;

import static io.obya.api.onboarding.domain.model.Scorecard.Dimension.FC;

@EnableConfigurationProperties(ScorerProperties.class)
@Configuration
public class ScorerConfig {

    @ConditionalOnProperty(name = "scorer.adapter", havingValue = "dummy")
    @Bean
    public ScorerDelegate dummyScorer() {
        return new ScorerDelegate() {
            @Override
            public Try<Scorecard> score(URI source, Contract.Type contract) {
                return Try.success(new Scorecard(
                        new Score(74),
                        Map.of(FC, new Score(99))));
            }

            @Override
            public Try<Scorecard> score(String source, Contract.Type contract) {
                return Try.success(new Scorecard(
                        new Score(55),
                        Map.of(FC, new Score(99))));
            }
        };
    }
}
