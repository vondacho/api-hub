package io.obya.api.onboarding.domain.model;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public interface DomainExamples {

    interface Specifications {
        Supplier<SpecificationId> id123 = () -> new SpecificationId("123");
        Supplier<SpecificationId> id456 = () -> new SpecificationId("456");

        static Specification specificationOf(SpecificationId id, String name, String productName,
                                             Version version, Revision revision,
                                             String body) {
            return new Specification(
                    new Info("test", "test", version),
                    Contract.from(Contract.Version.OPENAPI_V30),
                    new Metadata(
                            name,
                            revision,
                            "test",
                            productName, null, null),
                    Scores.scorecard.get(),
                    body,
                    List.of(),
                    id);
        }
    }

    interface Components {
        Supplier<Component> petstoreQuarkus = () -> new Component("petstore-quarkus", Revision.V100);
    }

    interface Contracts {
        Supplier<Contract> openApiV30 = () -> Contract.from(Contract.Version.OPENAPI_V30);
        Supplier<Contract> asyncApiV30 = () -> Contract.from(Contract.Version.ASYNCAPI_V30);
    }

    interface Scores {
        int acceptableEvaluation = Score.Grade.A.min;
        int tooLowEvaluation = Score.Grade.D.min;

        Function<Integer, Scorecard> fundationalCompliance = (fc) -> new Scorecard(
                new Score(fc),
                Map.of(Scorecard.Dimension.FC, new Score(fc)));

        Supplier<Scorecard> scorecard = () -> fundationalCompliance.apply(74);
        Supplier<Scorecard> acceptable = () -> Scorecard.globalOf(acceptableEvaluation);
        Supplier<Scorecard> tooLow = () -> Scorecard.globalOf(tooLowEvaluation);
    }
}
