package io.obya.api.onboarding.appl.usecase.processing.oas;

import io.obya.api.onboarding.appl.usecase.processing.CandidateSource;
import io.obya.api.onboarding.appl.usecase.processing.Processor;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIReader;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.*;
import io.obya.common.util.Try;
import io.openapiparser.*;
import io.openapiprocessor.jackson.JacksonConverter;
import io.openapiprocessor.jackson.JacksonJsonWriter;
import io.openapiprocessor.jsonschema.reader.StringReader;
import io.openapiprocessor.jsonschema.schema.*;

import java.io.IOException;
import java.io.StringWriter;

import static io.obya.api.onboarding.domain.model.Violation.Code.*;
import static io.obya.api.onboarding.appl.usecase.processing.Validator.*;
import static io.obya.api.onboarding.domain.model.Metadata.*;
import static java.util.Optional.ofNullable;

abstract class OASParser<M> implements Processor<State> {

    private final URIReader[] readers;

    public OASParser(URIReader...readers) {
        this.readers = readers;
    }

    @Override
    public Try<State> process(Try<State> state) {
        return state
            .filter(CandidateSource::isPresent, MISSING_DATA.failure( "state.source"), true)
            .flatMap(st -> {
                try {
                    final ParsingResult<M> parsing = parse(st);
                    return Try.success(st)
                        .flatMap(s -> setModel(s, parsing.model))
                        .flatMap(s -> setInfo(s, parsing.model))
                        .flatMap(s -> setMetadata(s, parsing.model))
                        .flatMap(s -> setBody(s, parsing.result));

                } catch (IllegalArgumentException | IOException e) {
                    return Try.failure(PROCESSING_FAILED.failure(CandidateSource.label(st), e).get());
                } catch (ParserException e) {
                    return Try.failure(PROCESSING_FAILED.failure(CandidateSource.label(st), e.getCause().getCause()).get());
                }
            });
    }

    record ParsingResult<M>(M model, OpenApiResult result) {}

    ParsingResult<M> parse(State state) throws IllegalArgumentException, IOException, ParserException {
        final String content = CandidateSource.read(state, readers);
        final DocumentLoader loader = new DocumentLoader(new StringReader(content), new JacksonConverter());
        final DocumentStore documents = new DocumentStore();
        final OpenApiResult result = new OpenApiParser(documents, loader).parse(CandidateSource.documentUri(state));
        return new ParsingResult<>(initModel(result), result);
    }

    protected abstract M initModel(OpenApiResult result);

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

    protected Try<State> setBody(State state, OpenApiResult parsingResult) {
        return Try.success(state.body(() -> {
            try {
                return toJson(parsingResult);
            } catch (IOException e) {
                return e.getMessage();
            }
        }));
    }

    protected static String toJson(OpenApiResult parsingResult) throws IOException {
        StringWriter stringWriter = new StringWriter();
        parsingResult.write(new JacksonJsonWriter(stringWriter));
        return stringWriter.toString();
    }
}
