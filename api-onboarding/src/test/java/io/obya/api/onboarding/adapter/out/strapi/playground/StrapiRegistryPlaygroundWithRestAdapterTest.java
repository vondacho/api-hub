package io.obya.api.onboarding.adapter.out.strapi.playground;

import io.obya.api.onboarding.adapter.out.strapi.StrapiRegistryRestAdapter;
import io.obya.api.onboarding.appl.usecase.UsecaseExamples;
import io.obya.api.onboarding.domain.model.*;
import io.obya.common.util.Try;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.bodyOf;
import static io.obya.api.onboarding.domain.model.DomainExamples.Specifications.specificationOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

@Disabled("E2E playground for investigation and troubleshooting")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = NONE)
class StrapiRegistryPlaygroundWithRestAdapterTest {

    @DynamicPropertySource
    static void configureStrapiUrl(DynamicPropertyRegistry registry) {
        registry.add("registry.adapter", () -> "strapi");
        // Strapi instance
        // registry.add("strapi.base-url", () -> "http://localhost:1337/api");
        // Microcks instance
        // registry.add("strapi.base-url", () -> "http://localhost:8085/rest/API+Registry+-+Specification/v1");
    }

    @Autowired
    private StrapiRegistryRestAdapter adapter;

    private static SpecificationId specificationId = null;

    @Order(0)
    @Test
    void should_register_valid_openapi_specification() throws Exception {
        try {
            Specification specification = specificationOf(
                    null,
                    "petstore", "platform",
                    Version.V1,
                    Revision.from("1.0." + RandomUtils.secure().randomInt()),
                    bodyOf(UsecaseExamples.Sources.Oas.validCandidate.get()));

            Try<SpecificationId> result = adapter.register(specification);
            assertThat(result.isSuccess()).isTrue();
            System.out.println(specificationId = result.getOrThrow());
        } catch (RestClientException e) {
            fail(e.getMessage());
        }
    }

    @Order(1)
    @Test
    void should_get_specification_resource_detail() {
        try {
            System.out.printf("Get specification resource detail %s%n", specificationId);
            Try<Specification> result = adapter.at(specificationId);
            assertThat(result.isSuccess()).isTrue();
            System.out.println(result.getOrThrow());
        } catch (RestClientException e) {
            fail(e.getMessage());
        }
    }

    @Order(2)
    @Test
    void should_list_specification_resources() {
        try {
            System.out.println("List all specification resources");
            Try<List<Specification>> result = adapter.all();
            assertThat(result.isSuccess()).isTrue();
            System.out.println(result.getOrThrow());
        } catch (RestClientException e) {
            fail(e.getMessage());
        }
    }

    @Order(3)
    @Test
    void should_get_specification_resource_at_revision_v100() {
        try {
            System.out.printf("Get specification resource at version %s revision %s%n", Version.V1, Revision.V100);
            Try<Specification> result = adapter.revisionAt("petstore", "platform", Version.V1, Revision.V100);
            assertThat(result.isSuccess()).isTrue();
            System.out.println(result.getOrThrow());
        } catch (RestClientException e) {
            fail(e.getMessage());
        }
    }

    @Order(4)
    @Test
    void should_get_specification_resource_at_revision_v101() {
        try {
            System.out.printf("Get specification resource at version %s revision %s%n", Version.V1, Revision.V101);
            Try<Specification> result = adapter.revisionAt("petstore", "platform", Version.V1, Revision.V101);
            assertThat(result.isSuccess()).isTrue();
            System.out.println(result.getOrThrow());
        } catch (RestClientException e) {
            fail(e.getMessage());
        }
    }

}
