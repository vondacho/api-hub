package io.obya.api.onboarding.appl.usecase.config;

import io.obya.api.onboarding.appl.out.ScorerDelegate;
import io.obya.api.onboarding.appl.out.Registry;
import io.obya.api.onboarding.appl.usecase.RegistrationService;
import io.obya.api.onboarding.appl.usecase.processing.reader.ClasspathResourceReader;
import io.obya.api.onboarding.appl.usecase.processing.*;
import io.obya.api.onboarding.appl.usecase.processing.aas.AASV20Parser;
import io.obya.api.onboarding.appl.usecase.processing.aas.AASV26Parser;
import io.obya.api.onboarding.appl.usecase.processing.aas.AASV30Parser;
import io.obya.api.onboarding.appl.usecase.processing.oas.OASV30Parser;
import io.obya.api.onboarding.appl.usecase.processing.oas.OASV31Parser;
import io.obya.api.onboarding.appl.usecase.processing.oas.OASV32Parser;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIFileReader;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIHttpReader;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIReader;
import io.obya.api.onboarding.domain.model.Contract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Map;

@Configuration
public class RegistrationConfig {

    private final URIReader[] readers = {
            new ClasspathResourceReader(),
            new URIHttpReader(),
            new URIFileReader()
    };

    @Bean
    public RegistrationService registrationService(Registry registry, ScorerDelegate remoteScorer) {
        return new RegistrationService(
            receptionist(),
            parser(),
            scorer(remoteScorer),
            scoreOverlayer(),
            componentOverlayer(),
            revisor(registry),
            registry);
    }

    public Receptionist receptionist() {
        return new Receptionist(readers);
    }

    public Scorer scorer(ScorerDelegate scorer) {
        return new Scorer(scorer);
    }

    public Parser parser() {
        return new Parser(Map.of(
                Contract.Version.OPENAPI_V30, () -> new OASV30Parser(readers),
                Contract.Version.OPENAPI_V31, () -> new OASV31Parser(readers),
                Contract.Version.OPENAPI_V32, () -> new OASV32Parser(readers),
                Contract.Version.ASYNCAPI_V20, () -> new AASV20Parser(readers),
                Contract.Version.ASYNCAPI_V26, () -> new AASV26Parser(readers),
                Contract.Version.ASYNCAPI_V30, () -> new AASV30Parser(readers)));
    }

    public Revisor revisor(Registry registry) {
        return new Revisor(registry);
    }

    public Overlayer scoreOverlayer() {
        return Overlayer.fromClasspath(classpathOf("scores_v1.overlay.yaml"),
                (state, _) -> Map.of(
                    "score", state.score()
        ));
    }

    public Overlayer componentOverlayer() {
        return Overlayer.fromClasspath(classpathOf("component_v1.overlay.yaml"),
                (state, _) -> Map.of(
                "name", state.metadata().componentName(),
                "revision", state.metadata().componentRevision().semver().getVersion()
        ));
    }

    private URI classpathOf(String filename) {
        return URI.create("classpath:///api/registration/" + filename);
    }
}
