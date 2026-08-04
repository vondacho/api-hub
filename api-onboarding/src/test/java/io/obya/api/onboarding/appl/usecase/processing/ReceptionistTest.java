package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.usecase.UsecaseExamples;
import io.obya.api.onboarding.appl.usecase.processing.reader.URIReader;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import io.obya.api.onboarding.domain.model.Contract;
import io.obya.api.onboarding.domain.model.Violation;
import io.obya.common.util.Try;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.obya.api.onboarding.domain.model.Violation.Code.MISSING_DATA;
import static io.obya.api.onboarding.domain.model.Violation.Code.PROCESSING_FAILED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Behavioural specification of the {@link Receptionist} processor.
 *
 * <p>The Receptionist takes a workflow {@link State} carrying a source URI, reads the first
 * line of that source, and attaches the identified {@link Contract} (type + version) to the
 * state. When the source is missing or cannot be read/recognised it records a violation
 * instead of a contract.</p>
 *
 * <p>Tests describe <em>what</em> the Receptionist guarantees to its caller — the resulting
 * {@code Try<State>} and the violation codes it reports. The {@link URIReader} collaborators
 * are Mockito mocks configured per scenario.</p>
 */
class ReceptionistTest {

    private static URIReader readerYielding(String firstLine) throws IOException {
        URIReader reader = mock(URIReader.class);
        when(reader.canRead(any())).thenReturn(true);
        when(reader.firstLineOnly(any())).thenReturn(firstLine);
        return reader;
    }

    private static URIReader readerThatCannotRead() {
        URIReader reader = mock(URIReader.class);
        when(reader.canRead(any())).thenReturn(false);
        return reader;
    }

    private static URIReader readerFailingWith(IOException failure) throws IOException {
        URIReader reader = mock(URIReader.class);
        when(reader.canRead(any())).thenReturn(true);
        when(reader.firstLineOnly(any())).thenThrow(failure);
        return reader;
    }

    private static Try<State> receive(URIReader reader, State state) {
        return new Receptionist(reader).process(Try.success(state));
    }

    private static Violation.Failure onlyFailure(Try<State> result) {
        assertTrue(result.isFailure(), () -> "expected a failure but was: " + result);
        assertEquals(1, result.getExceptions().size(), "expected exactly one violation");
        Exception e = result.getExceptions().getFirst();
        assertInstanceOf(Violation.Failure.class, e);
        return (Violation.Failure) e;
    }

    @Nested
    class IdentifiesTheContract {

        @Test
        void attachesOpenApiContractWithDetectedVersion() throws IOException {
            Try<State> result = receive(readerYielding("openapi: 3.0.3"),
                    new State().source(UsecaseExamples.Sources.Oas.validCandidate.get()));

            assertTrue(result.isSuccess());
            Contract contract = result.getValue().orElseThrow().contract();
            assertEquals(Contract.Type.OPENAPI, contract.type());
            assertEquals(Contract.Version.OPENAPI_V30, contract.version());
        }

        @Test
        void attachesAsyncApiContractWithDetectedVersion() throws IOException {
            Try<State> result = receive(readerYielding("asyncapi: 3.0.0"),
                    new State().source(UsecaseExamples.Sources.Oas.validCandidate.get()));

            assertTrue(result.isSuccess());
            Contract contract = result.getValue().orElseThrow().contract();
            assertEquals(Contract.Type.ASYNCAPI, contract.type());
            assertEquals(Contract.Version.ASYNCAPI_V30, contract.version());
        }

        @Test
        void leavesTheOriginalSourceUntouched() throws IOException {
            Try<State> result = receive(readerYielding("openapi: 3.1.0"),
                    new State().source(UsecaseExamples.Sources.Oas.validCandidate.get()));

            assertEquals(UsecaseExamples.Sources.Oas.validCandidate.get(),
                    result.getValue().orElseThrow().source());
        }
    }

    @Nested
    class RejectsUnusableInput {

        @Test
        void reportsMissingDataWhenSourceIsAbsent() throws IOException {
            Try<State> result = receive(readerYielding("openapi: 3.0.3"), new State());

            assertEquals(MISSING_DATA, onlyFailure(result).code);
        }

        @Test
        void reportsProcessingFailedWhenNoReaderCanHandleTheSource() {
            Try<State> result = receive(readerThatCannotRead(),
                    new State().source(UsecaseExamples.Sources.Oas.validCandidate.get()));

            assertEquals(PROCESSING_FAILED, onlyFailure(result).code);
        }

        @Test
        void reportsProcessingFailedWhenReadingThrows() throws IOException {
            Try<State> result = receive(readerFailingWith(new IOException("unreachable")),
                    new State().source(UsecaseExamples.Sources.Oas.validCandidate.get()));

            assertEquals(PROCESSING_FAILED, onlyFailure(result).code);
        }

        @Test
        void reportsProcessingFailedWhenContentMatchesNoKnownContractType() throws IOException {
            Try<State> result = receive(readerYielding("this is not a contract"),
                    new State().source(UsecaseExamples.Sources.Oas.validCandidate.get()));

            assertEquals(PROCESSING_FAILED, onlyFailure(result).code);
        }
    }

    @Nested
    class HonoursIncomingFailures {

        @Test
        void propagatesAnAlreadyFailedStateWithoutReading() throws IOException {
            URIReader reader = mock(URIReader.class);
            Try<State> incoming = Try.failure(new IllegalStateException("upstream failed"));

            Try<State> result = new Receptionist(reader).process(incoming);

            assertTrue(result.isFailure());
            verify(reader, never()).firstLineOnly(any());
        }
    }
}
