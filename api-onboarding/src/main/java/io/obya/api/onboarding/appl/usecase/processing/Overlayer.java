package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.usecase.processing.oai.OverlayParser;
import io.obya.api.onboarding.appl.usecase.processing.oai.OverlayV10Parser;
import io.obya.api.onboarding.appl.usecase.processing.reader.ClasspathResourceReader;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIFileReader;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIHttpReader;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIReader;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.common.util.Try;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class Overlayer implements Processor<State> {

    private final URI uri;
    private final OverlayParser strategy;

    public Overlayer(URI uri, OverlayParser strategy) {
       this.uri = uri;
       this.strategy = strategy;
    }

    public static Overlayer from(URI overlay, BiFunction<State, List<Exception>, Object> mapping) {
        URIReader[] readers = { new ClasspathResourceReader(), new URIHttpReader(), new URIFileReader() };
        return new Overlayer(overlay, new OverlayV10Parser(readers, mapping));
    }

    public static Overlayer fromClasspath(URI overlay, BiFunction<State, List<Exception>, Object> mapping) {
        URIReader[] readers = { new ClasspathResourceReader() };
        return new Overlayer(overlay, new OverlayV10Parser(readers, mapping));
    }

    @Override
    public Try<State> process(Try<State> state) {
       return state.flatMap(st -> {
           final URI originalUri = st.source();
           return strategy.process(Try.success(st.source(uri))).map(s -> s.source(originalUri));
       });
    }
}
