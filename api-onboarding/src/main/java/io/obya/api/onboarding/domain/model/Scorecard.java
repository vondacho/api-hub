package io.obya.api.onboarding.domain.model;

import java.util.Map;

public record Scorecard(Score global, Map<Dimension, Score> dimensions) {

    public enum Dimension {
        FC("Foundational Compliance", "Base layer of spec validity and structural soundness."),
        SEC("Security", "Trust, risk posture, and security compliance."),
        DX("Developer Experience", "Clarity, completeness, and ingestion readiness for developers and tooling."),
        MR("Mock Readiness", "Assesses the API's readiness for mock testing and development."),
        ARAX("AI-Readiness & Agent Experience", "Semantic breadth, depth, and agent comprehension for AI systems."),
        AU("Agent Usability", "Functional utility, complexity comfort, and AI orchestration readiness."),
        AID("AI Discoverability", "Findability, semantic richness, and reasoning readiness.");

        public final String name;
        public final String intention;

        Dimension(String name, String intention) {
            this.name = name;
            this.intention = intention;
        }
    }

    public static Scorecard undefined() {
        return new Scorecard(Score.undefined(), Map.of());
    }

    public static Scorecard globalOf(int evaluation) {
        return new Scorecard(new Score(evaluation), Map.of());
    }

    public boolean isUndefined() {
        return global.isUndefined() && dimensions.isEmpty();
    }

    public boolean isTooLow() {
        return global.isTooLow();
    }
}
