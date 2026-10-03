package io.obya.api.onboarding.appl.usecase.processing;

import io.obya.api.onboarding.appl.usecase.processing.reader.URIReader;
import io.obya.api.onboarding.appl.usecase.workflow.State;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static io.obya.api.onboarding.appl.usecase.processing.reader.URIReader.readerFor;

/**
 * A candidate arrives as a string that is either a location to fetch the specification from or
 * the specification document itself. This is where the two are told apart and where the text of
 * a candidate is obtained, whichever way it came.
 */
public final class CandidateSource {

    /** Document URI handed to parsers that need one when the specification was sent inline. */
    public static final URI INLINE = URI.create("inline:///candidate");

    private CandidateSource() {
    }

    /**
     * A single-line absolute URI is a location; anything else is the document. A document never
     * parses as an absolute URI: YAML carries a space after its first key and JSON opens on a brace.
     * An unsupported scheme stays a location so that the readers reject it explicitly.
     */
    public static State toState(String candidate) {
        final State state = new State();
        if (candidate == null || candidate.isBlank()) {
            return state;
        }
        final String trimmed = candidate.strip();
        if (trimmed.lines().count() == 1) {
            try {
                final URI uri = new URI(trimmed);
                if (uri.isAbsolute()) {
                    return state.source(uri);
                }
            } catch (URISyntaxException _) {
                // not a location, hence the document itself
            }
        }
        return state.content(candidate);
    }

    public static boolean isPresent(State state) {
        return state.source() != null || state.content() != null;
    }

    public static String read(State state, URIReader... readers) throws IOException {
        if (state.content() != null) {
            return state.content();
        }
        return readerFor(state.source(), readers).allInOne(state.source());
    }

    public static String firstLine(State state, URIReader... readers) throws IOException {
        if (state.content() != null) {
            return state.content().strip().lines().findFirst().orElse("");
        }
        return readerFor(state.source(), readers).firstLineOnly(state.source());
    }

    /** The document URI a parser resolves against. */
    public static URI documentUri(State state) {
        return state.content() != null ? INLINE : state.source();
    }

    /** How a candidate is named in a violation. */
    public static Object label(State state) {
        return state.content() != null ? "inline content" : state.source();
    }
}
