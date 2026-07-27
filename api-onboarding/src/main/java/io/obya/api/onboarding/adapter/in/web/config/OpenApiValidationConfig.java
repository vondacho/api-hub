package io.obya.api.onboarding.adapter.in.web.config;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.springmvc.OpenApiValidationFilter;
import com.atlassian.oai.validator.springmvc.OpenApiValidationInterceptor;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures OpenAPI contract validation for all HTTP requests/responses.
 * Validates incoming requests and outgoing responses against the OpenAPI specContent.
 */
@Configuration
public class OpenApiValidationConfig implements WebMvcConfigurer {

    @Bean
    public OpenApiValidationInterceptor openApiValidationInterceptor() {
        var parseOptions = new ParseOptions();
        parseOptions.setResolveFully(true);
        parseOptions.setResolveCombinators(true);
        parseOptions.setFlattenComposedSchemas(true);
        return new OpenApiValidationInterceptor(OpenApiInteractionValidator
                .createForSpecificationUrl("api/registration/registration_v1.openapi.yaml")
                .withParseOptions(parseOptions)
                .build());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openApiValidationInterceptor())
                .addPathPatterns("/registrations/**");
    }

    @Bean
    public OpenApiValidationFilter openApiValidationFilter() {
        return new OpenApiValidationFilter(true, false);
    }

}
