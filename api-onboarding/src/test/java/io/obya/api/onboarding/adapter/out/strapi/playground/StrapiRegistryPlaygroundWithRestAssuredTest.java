package io.obya.api.onboarding.adapter.out.strapi.playground;

import io.obya.api.onboarding.adapter.in.web.model.ScoreSummary;
import io.obya.api.onboarding.adapter.out.strapi.model.SpecificationPostSpecificationsRequest;
import io.obya.api.onboarding.adapter.out.strapi.model.SpecificationPostSpecificationsRequestData;
import io.obya.api.onboarding.domain.model.Revision;
import io.obya.api.onboarding.domain.model.Specification;
import io.obya.api.onboarding.domain.model.SpecificationId;
import io.obya.api.onboarding.domain.model.Version;
import io.restassured.RestAssured;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.*;

import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.bodyOf;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.validCandidateUri;
import static io.obya.api.onboarding.domain.model.DomainExamples.Specifications.specificationOf;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;

@Disabled("E2E playground for investigation and troubleshooting")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StrapiRegistryPlaygroundWithRestAssuredTest {

    private static SpecificationId specificationId = null;

    @BeforeEach
    void setUp() {
        RestAssured.port = 1337;
        RestAssured.basePath = "/api";
    }

    @Order(2)
    @Test
    void should_list_specification_resources() {
        when()
                .request("GET", "/specifications")
                .then()
                .statusCode(200)
                .log();
    }

    @Order(1)
    @Test
    void should_get_specification_resource_detail() {
        given()
                .when()
                .get("/specifications/%s".formatted(specificationId.id()))
                .peek()
                .then()
                .statusCode(200);
    }

    @Order(0)
    @Test
    void should_register_valid_openapi_specification() throws Exception {
        specificationId = new SpecificationId(
            given()
                .when()
                .body(requestOf(specificationOf(
                        null,
                        "petstore", "platform",
                        Version.V1,
                        Revision.from("1.0." + RandomUtils.secure().randomInt()),
                        bodyOf(validCandidateUri.get()))))
                .post("/specifications")
                .peek()
                .then()
                .statusCode(201)
                .extract()
                .path("id"));
    }

    private SpecificationPostSpecificationsRequest requestOf(Specification specification) {
        return new SpecificationPostSpecificationsRequest().data(
                new SpecificationPostSpecificationsRequestData()
                        .specId(String.valueOf(RandomUtils.secure().randomInt()))
                        .name(specification.metadata().name())
                        .version(specification.info().version().format())
                        .revision(specification.metadata().revision().format())
                        .productName(specification.metadata().productName())
                        .bundleName(specification.metadata().bundleName())
                        .contract(SpecificationPostSpecificationsRequestData.ContractEnum.valueOf(
                                specification.contract().version().name()))
                        .body(specification.body())
                        .score(ScoreSummary.from(specification.score()))
                        .violations(specification.violations())
        );
    }
}
