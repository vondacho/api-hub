package io.obya.api.onboarding.adapter.out.jentic.config;

import io.obya.api.onboarding.adapter.out.jentic.JenticRestAdapter;
import io.obya.api.onboarding.appl.out.ScorerDelegate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(JenticClientProperties.class)
@Configuration
public class JenticClientConfig {

    @ConditionalOnProperty(name = "scorer.adapter", havingValue = "jentic")
    @Bean
    public ScorerDelegate jenticScorer() {
        return new JenticRestAdapter();
    }
}
