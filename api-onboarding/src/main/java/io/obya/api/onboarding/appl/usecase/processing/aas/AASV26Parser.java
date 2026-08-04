package io.obya.api.onboarding.appl.usecase.processing.aas;

import com.asyncapi.v2._6_0.model.AsyncAPI;
import com.asyncapi.v2._6_0.model.info.Info;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIReader;

import java.util.Optional;

import static io.obya.api.onboarding.domain.model.Metadata.*;

public class AASV26Parser extends AASParser<AsyncAPI> {

    public AASV26Parser(URIReader... readers) {
        super(readers);
    }

    @Override
    protected AsyncAPI initModel(String content) throws JsonProcessingException {
        var objectMapper = new ObjectMapper(new YAMLFactory())
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper.readValue(content, AsyncAPI.class);
    }

    @Override
    protected String getTitle(AsyncAPI model) {
        return model.getInfo().getTitle();
    }

    @Override
    protected String getDescription(AsyncAPI model) {
        return model.getInfo().getDescription();
    }

    @Override
    protected String getVersion(AsyncAPI model) {
        return model.getInfo().getVersion();
    }

    @Override
    protected String getName(AsyncAPI model) {
        return (String) Optional.of(model.getInfo())
                .map(Info::getExtensionFields)
                .map(fields -> fields.get(META_API_NAME_KEY))
                .orElse(null);
    }

    @Override
    protected String getRevision(AsyncAPI model) {
        return (String) Optional.of(model.getInfo())
                .map(Info::getExtensionFields)
                .map(fields -> fields.get(META_API_REVISION_KEY))
                .orElse(null);
    }

    @Override
    protected String getBundleName(AsyncAPI model) {
        return (String) Optional.of(model.getInfo())
                .map(Info::getExtensionFields)
                .map(fields -> fields.get(META_BUNDLE_NAME_KEY))
                .orElse(null);
    }

    @Override
    protected String getProductName(AsyncAPI model) {
        return (String) Optional.of(model.getInfo())
                .map(Info::getExtensionFields)
                .map(fields -> fields.get(META_PRODUCT_NAME_KEY))
                .orElse(null);
    }

    @Override
    protected String getComponentName(AsyncAPI model) {
        return (String) Optional.of(model.getInfo())
                .map(Info::getExtensionFields)
                .map(fields -> fields.get(META_COMPONENT_NAME_KEY))
                .orElse(null);
    }

    @Override
    protected String getComponentVersion(AsyncAPI model) {
        return (String) Optional.of(model.getInfo())
                .map(Info::getExtensionFields)
                .map(fields -> fields.get(META_COMPONENT_REVISION_KEY))
                .orElse(null);
    }
}
