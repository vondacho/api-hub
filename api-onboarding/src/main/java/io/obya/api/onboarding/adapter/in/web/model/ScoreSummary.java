package io.obya.api.onboarding.adapter.in.web.model;

import io.obya.api.onboarding.domain.model.Score;
import io.obya.api.onboarding.domain.model.Scorecard;
import io.obya.api.onboarding.domain.model.Violation;

import java.util.List;

public record ScoreSummary(int score, String grade, List<Dimension> dimensions, List<Violation> violations) {

    public record Dimension(String name, String intent, int score, String grade) {}

    public static ScoreSummary from(Scorecard scorecard) {
        return from(scorecard, List.of());
    }

    public static ScoreSummary from(Scorecard scorecard, List<Violation> violations) {
        return new ScoreSummary(
                scorecard.global().evaluation(),
                gradeIt(scorecard.global()),
                scorecard.dimensions().entrySet().stream()
                        .map(entry -> new Dimension(
                                entry.getKey().name(),
                                entry.getKey().intention,
                                entry.getValue().evaluation(),
                                gradeIt(entry.getValue()))).toList(),
                violations);
    }

    private static String gradeIt(Score score) {
        var grade = score.grade();
        return "%s%s".formatted(grade.name(),
                grade.strong(score.evaluation()) ? "+" : grade.weak(score.evaluation()) ? "-" : "");
    }
}
