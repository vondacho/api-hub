package io.obya.api.onboarding.appl.usecase.workflow;

import io.obya.api.onboarding.domain.model.Status;
import io.obya.api.onboarding.domain.model.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

@Data
@Accessors(chain = true, fluent = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class State {
    SpecificationId id;
    URI source;
    String content;
    Info info;
    Contract contract;
    Metadata metadata;
    Status status;
    Scorecard score;
    Object model;
    Supplier<String> body;

    public Specification toSpecification() {
        return toSpecification(List.of());
    }

    public Specification toSpecification(List<Exception> failures) {
        return new Specification(
                info(),
                contract(),
                metadata(),
                score(),
                body().get(),
                Violation.from(failures),
                id());
    }

    public static State from(Specification specification) {
        return new State()
                .id(specification.id())
                .info(specification.info())
                .contract(specification.contract())
                .metadata(specification.metadata())
                .body(specification::body);
    }
}
