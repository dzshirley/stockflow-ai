package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockflow.analytics.AnalyticsModels.PeriodPreset;
import com.stockflow.analytics.AnalyticsModels.TrendInterval;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MockAiIntentPlannerEvaluationTest {

    private final MockAiIntentPlanner planner = new MockAiIntentPlanner();

    @Test
    void evaluationSuiteHasBroadCoverage() {
        assertThat(cases()).hasSizeGreaterThanOrEqualTo(50);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caseStream")
    void routesBusinessQuestionsDeterministically(EvaluationCase evaluation) {
        var result = planner.plan(evaluation.question(), List.of());

        assertThat(result.preset()).isEqualTo(evaluation.preset());
        assertThat(result.interval()).isEqualTo(evaluation.interval());
        assertThat(result.limit()).isEqualTo(evaluation.limit());
        assertThat(result.tools()).containsExactlyElementsOf(evaluation.tools());
    }

    @Test
    void resolvesShortFollowUpFromBoundedConversationHistory() {
        var history = List.of(new AiModels.HistoryMessage("USER", "Show revenue for last month"));

        var result = planner.plan("Visualize that weekly", history);

        assertThat(result.preset()).isEqualTo(PeriodPreset.LAST_MONTH);
        assertThat(result.interval()).isEqualTo(TrendInterval.WEEK);
        assertThat(result.tools()).containsExactly(AnalyticsToolAdapter.REVENUE_TREND);
    }

    static Stream<EvaluationCase> caseStream() {
        return cases().stream();
    }

    static List<EvaluationCase> cases() {
        var stream = MockAiIntentPlannerEvaluationTest.class.getResourceAsStream("/ai-evaluation-cases.csv");
        if (stream == null) throw new IllegalStateException("AI evaluation fixture is missing");
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank()).map(MockAiIntentPlannerEvaluationTest::parse).toList();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to read AI evaluation fixture", exception);
        }
    }

    private static EvaluationCase parse(String line) {
        String[] fields = line.split("\\|", -1);
        var interval = fields[3].isBlank() ? null : TrendInterval.valueOf(fields[3]);
        var tools = fields[5].isBlank() ? List.<String>of() : Arrays.asList(fields[5].split(";"));
        return new EvaluationCase(fields[0], fields[1], PeriodPreset.valueOf(fields[2]), interval,
                Integer.parseInt(fields[4]), tools);
    }

    record EvaluationCase(
            String name,
            String question,
            PeriodPreset preset,
            TrendInterval interval,
            int limit,
            List<String> tools) {

        @Override
        public String toString() {
            return name;
        }
    }
}
