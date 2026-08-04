package io.obya.api.onboarding.appl.usecase.processing.aas;

import io.obya.api.onboarding.appl.usecase.processing.Processor;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIReader;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.Info;
import io.obya.api.onboarding.domain.model.Metadata;
import io.obya.api.onboarding.domain.model.Revision;
import io.obya.api.onboarding.domain.model.Version;
import io.obya.common.util.Try;

import java.io.IOException;
import java.net.URI;

import static io.obya.api.onboarding.domain.model.Violation.Code.*;
import static io.obya.api.onboarding.appl.usecase.processing.Validator.*;
import static io.obya.api.onboarding.appl.usecase.processing.reader.URIReader.readerFor;
import static io.obya.api.onboarding.domain.model.Metadata.*;
import static io.obya.api.onboarding.domain.model.Metadata.META_PRODUCT_NAME_KEY;
import static java.util.Optional.ofNullable;

abstract class AASParser<M> implements Processor<State> {

    private final URIReader[] readers;

    public AASParser(URIReader...readers) {
        this.readers = readers;
    }

    @Override
    public Try<State> process(Try<State> state) {
        return state
            .filter(st -> nonNull(st::source), MISSING_DATA.failure( "state.source"), true)
            .flatMap(st -> {
                try {
                    final ParsingResult<M> parsing = parse(st.source());
                    return Try.success(st)
                        .flatMap(s -> setModel(s, parsing.model))
                        .flatMap(s -> setInfo(s, parsing.model))
                        .flatMap(s -> setMetadata(s, parsing.model))
                        .flatMap(s -> setBody(s, parsing));

                } catch (IllegalArgumentException | IOException e) {
                    return Try.failure(PROCESSING_FAILED.failure(st.source(), e).get());
                }
            });
    }

    record ParsingResult<M>(M model, String content) {}

    ParsingResult<M> parse(URI source) throws IllegalArgumentException, IOException {
        final String content = readerFor(source, readers).allInOne(source);
        return new ParsingResult<>(initModel(content), content);
    }

    abstract M initModel(String result) throws IOException;

    protected Try<State> setModel(State state, M model) {
        return Try.success(state.model(model));
    }

    protected Try<State> setInfo(State state, M model) {
        return Try.success(new Info(
                        getTitle(model),
                        getDescription(model),
                        Version.from(getVersion(model)))
                )
                .filter(i -> nonEmpty(i::title), MISSING_DATA.failure("info.title"), true)
                .filter(i -> nonEmpty(i::description), MISSING_DATA.failure("info.description"), true)
                .filter(i -> nonNull(i::version), MISSING_DATA.failure("info.version"), true)
                .map(state::info);
    }

    protected abstract String getTitle(M model);
    protected abstract String getDescription(M model);
    protected abstract String getVersion(M model);

    protected Try<State> setMetadata(State state, M model) {
        return Try.success(new Metadata(

                        getName(model),
                        ofNullable(getRevision(model))
                                .map(s -> Revision.from(s, MALFORMED_REVISION.failure(META_API_REVISION_KEY, "semver")))
                                .orElse(null),
                        getBundleName(model),
                        getProductName(model),
                        getComponentName(model),
                        ofNullable(getComponentVersion(model))
                                .map(s -> Revision.from(s, MALFORMED_REVISION.failure(META_COMPONENT_REVISION_KEY, "semver")))
                                .orElse(null)
                ))
                .filter(m -> nonEmpty(m::name), MISSING_DATA.failure(META_API_NAME_KEY), true)
                .filter(m -> nonEmpty(m::productName), MISSING_DATA.failure(META_PRODUCT_NAME_KEY), true)
                .filter(m -> nonEmpty(m::bundleName), MISSING_DATA.failure(META_BUNDLE_NAME_KEY), true)
                .map(state::metadata);
    }

    protected abstract String getName(M model);
    protected abstract String getRevision(M model);
    protected abstract String getBundleName(M model);
    protected abstract String getProductName(M model);
    protected abstract String getComponentName(M model);
    protected abstract String getComponentVersion(M model);

    protected Try<State> setBody(State state, ParsingResult<M> parsingResult) {
        return Try.success(state.body(() -> parsingResult.content));
    }
}
