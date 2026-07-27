package io.obya.api.onboarding.appl.out;

import io.obya.api.onboarding.domain.model.Revision;
import io.obya.api.onboarding.domain.model.Specification;
import io.obya.api.onboarding.domain.model.SpecificationId;
import io.obya.api.onboarding.domain.model.Version;
import io.obya.common.util.Try;

public interface Registry {

    Try<SpecificationId> register(Specification specification);

    Try<Specification> at(SpecificationId id, String...attributes);

    Try<Specification> latestAt(String name, String productName, Version version, String...attributes);

    Try<Specification> revisionAt(String name, String productName, Version version, Revision revision, String...attributes);

    default Try<Specification> at(SpecificationId id) {
        return at(id,
                "name",
                "productName",
                "version",
                "revision",
                "bundleName",
                "componentName",
                "componentRevision",
                "contract");
    }

    default Try<Specification> at(String name, String productName, Version version) {
        return latestAt(name, productName, version,
                "name",
                "productName",
                "version",
                "revision",
                "bundleName",
                "componentName",
                "componentRevision",
                "contract");
    }

    default Try<Specification> at(String name, String productName, Version version, Revision revision) {
        return revisionAt(name, productName, version, revision,
                "name",
                "productName",
                "version",
                "revision",
                "bundleName",
                "componentName",
                "componentRevision",
                "contract");
    }
}
