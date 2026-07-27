package io.obya.api.onboarding.adapter.out.spectral.config;

import io.obya.api.onboarding.adapter.out.scorer.api.ScoringApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@EnableConfigurationProperties(SpectralClientProperties.class)
@Configuration
public class SpectralClientConfig {

    @ConditionalOnProperty(name = "scorer.adapter", havingValue = "spectral")
    @Bean
    ScoringApi scorerApiClient(RestClient.Builder builder, SpectralClientProperties props) {
        RestClient restClient = builder
                .baseUrl(props.baseUrl().toString())
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(ScoringApi.class);
    }
}
