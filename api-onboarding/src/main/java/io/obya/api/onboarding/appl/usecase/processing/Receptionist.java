package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.usecase.processing.reader.URIReader;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.Contract;
import io.obya.common.util.Try;

import java.io.IOException;

import static io.obya.api.onboarding.domain.model.Violation.Code.MISSING_DATA;
import static io.obya.api.onboarding.domain.model.Violation.Code.PROCESSING_FAILED;

public class Receptionist implements Processor<State> {

    private final URIReader[] readers;

    public Receptionist(URIReader...readers) {
        this.readers = readers;
    }

    @Override
    public Try<State> process(Try<State> state) {
        return state
            .filter(CandidateSource::isPresent, MISSING_DATA.failure( "state.source"), true)
            .flatMap(st -> {
                try {
                    Contract contract = readContract(st);
                    return Try.success(st).map(s -> s.contract(contract));

                } catch (IllegalArgumentException | IOException e) {
                    return Try.failure(PROCESSING_FAILED.failure(CandidateSource.label(st), e).get());
                }
            });
    }

    private Contract readContract(State state) throws IOException, IllegalArgumentException {
        final String firstLine = CandidateSource.firstLine(state, readers);
        final Contract.Type sourceType = Contract.Type.findType(firstLine);
        final Contract.Version version = sourceType.findVersion(firstLine).orElse(null);
        return new Contract(sourceType, version);
    }
}
