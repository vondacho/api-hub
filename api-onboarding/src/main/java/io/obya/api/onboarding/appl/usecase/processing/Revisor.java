package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.out.Registry;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.*;
import io.obya.common.util.Try;

import java.util.List;

import static io.obya.api.onboarding.appl.usecase.processing.Validator.nonNull;
import static io.obya.api.onboarding.domain.model.Metadata.META_API_REVISION_KEY;
import static io.obya.api.onboarding.domain.model.Violation.Code.*;
import static java.util.Objects.isNull;

public class Revisor implements Processor<State> {

    private final Registry registry;

    public Revisor(Registry registry) {
        this.registry = registry;
    }

    @Override
    public Try<State> process(Try<State> state) {
        return state
            .filter(st -> nonNull(st::info), MISSING_DATA.failure("state.info"), true)
            .filter(st -> nonNull(st::metadata), MISSING_DATA.failure("state.metadata"), true)
            .flatMap(st -> {
                final Version major = st.info().version();

                Try<Revision> latest = registry
                        .at(st.metadata().name(), st.metadata().productName(), major)
                        .map(spec -> spec.metadata().revision());

                if (latest.isFailure()) {
                    return latest.recoverWithOther(_ -> enforcer().process(state));
                }
                return latest.flatMap(r -> enforcer(r).process(state));
            });
    }

    private Processor<State> enforcer() {
        return state -> state.flatMap(st -> {
            if (isNull(st.metadata().revision())) {
                return Try.success(alignRevisionOn(st, Revision.from(st.info().version())));
            }
            if (!st.metadata().revision().matches(st.info().version())) {
                return new Try.Partial<>(alignRevisionOn(st, Revision.from(st.info().version())),
                        List.of(REVISION_NOT_ALIGNED.failure(META_API_REVISION_KEY, st.info().version()).get()));
            }
            return Try.success(alignRevisionOn(st, st.metadata().revision()));
        });
    }

    private Processor<State> enforcer(Revision currentLatest) {
        return state -> state.flatMap(st -> {
            if (isNull(st.metadata().revision())) {
                return Try.success(alignRevisionOn(st, currentLatest.next()));
            }
            if (!st.metadata().revision().matches(st.info().version())) {
                return new Try.Partial<>(alignRevisionOn(st, currentLatest.next()),
                    List.of(REVISION_NOT_ALIGNED.failure(META_API_REVISION_KEY, currentLatest.next()).get()));
            }
            if (!st.metadata().revision().after(currentLatest)) {
                return new Try.Partial<>(alignRevisionOn(st, currentLatest.next()),
                    List.of(REVISION_AUTO_INCREMENTED.failure(META_API_REVISION_KEY, currentLatest.next()).get()));
            }
            return Try.success(alignRevisionOn(st, st.metadata().revision()));
        });
    }

    /**
     * Assigns the revision the candidate will be registered under and resets its implementation:
     * a new revision always requires a fresh component association.
     */
    private State alignRevisionOn(State st, Revision currentLatest) {
        return st.metadata(new Metadata(
            st.metadata().name(),
            currentLatest,
            st.metadata().bundleName(),
            st.metadata().productName(), null, null));
    }
}
