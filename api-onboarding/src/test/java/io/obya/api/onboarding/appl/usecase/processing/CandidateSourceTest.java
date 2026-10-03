package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.usecase.processing.reader.URIReader;
import io.obya.api.onboarding.appl.usecase.workflow.State;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.Inline.*;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.remoteSource;
import static io.obya.api.onboarding.appl.usecase.UsecaseExamples.Sources.unsupportedSchemeSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Behavioural specification of {@link CandidateSource}: how a submitted string is told apart as a
 * location to fetch or as the specification document itself, and how the text of a workflow
 * {@link State} is then obtained.
 */
class CandidateSourceTest {

    @Nested
    class ClassifiesTheSubmittedString {

        @Test
        void takesAnAbsoluteUrlAsALocation() {
            State state = CandidateSource.toState(remoteSource.get().toString());

            assertEquals(remoteSource.get(), state.source());
            assertNull(state.content());
        }

        @Test
        void toleratesSurroundingWhitespaceAroundALocation() {
            State state = CandidateSource.toState("  " + remoteSource.get() + "\n");

            assertEquals(remoteSource.get(), state.source());
        }

        @Test
        void keepsAnUnsupportedSchemeAsALocationSoTheReadersRejectIt() {
            State state = CandidateSource.toState(unsupportedSchemeSource.get().toString());

            assertEquals(unsupportedSchemeSource.get(), state.source());
        }

        @Test
        void takesADocumentAsInlineContent() {
            State state = CandidateSource.toState(validOasCandidate.get());

            assertNull(state.source());
            assertEquals(validOasCandidate.get(), state.content());
        }

        @Test
        void leavesABlankStringAsNoSourceAtAll() {
            State state = CandidateSource.toState(" \n ");

            assertFalse(CandidateSource.isPresent(state));
        }

        @Test
        void takesARelativeReferenceAsInlineContent() {
            State state = CandidateSource.toState(relativeReference.get());

            assertNull(state.source());
            assertEquals(relativeReference.get(), state.content());
        }
    }

    @Nested
    class ReadsTheText {

        @Test
        void returnsInlineContentWithoutConsultingAnyReader() throws IOException {
            URIReader reader = mock(URIReader.class);

            String text = CandidateSource.read(new State().content(validOasCandidate.get()), reader);

            assertEquals(validOasCandidate.get(), text);
            verifyNoInteractions(reader);
        }

        @Test
        void readsALocationThroughTheMatchingReader() throws IOException {
            URIReader reader = mock(URIReader.class);
            when(reader.canRead(any())).thenReturn(true);
            when(reader.allInOne(any())).thenReturn(validAasCandidate.get());

            String text = CandidateSource.read(new State().source(remoteSource.get()), reader);

            assertEquals(validAasCandidate.get(), text);
        }
    }
}
