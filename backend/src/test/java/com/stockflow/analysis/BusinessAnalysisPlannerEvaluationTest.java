package com.stockflow.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.analytics.AnalyticsModels;
import com.stockflow.analysis.BusinessAnalysisModels.AnalysisType;
import com.stockflow.analysis.BusinessAnalysisModels.CreateReportRequest;
import com.stockflow.common.error.ApiException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class BusinessAnalysisPlannerEvaluationTest {

    private final BusinessAnalysisPlanner planner = new BusinessAnalysisPlanner();

    @TestFactory
    Stream<DynamicTest> selectsSupportedAnalysisTypeAndRejectsUnsupportedRequests() throws Exception {
        var resource = getClass().getResourceAsStream("/business-analysis-evaluation-cases.csv");
        assertThat(resource).isNotNull();
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            var cases = reader.lines().filter(line -> !line.isBlank()).map(this::parse).toList();
            assertThat(cases).hasSizeBetween(30, 50);
            return cases.stream().map(item -> DynamicTest.dynamicTest(item.prompt(), () -> {
                var request = new CreateReportRequest(
                        item.prompt(), AnalysisType.AUTO, AnalyticsModels.PeriodPreset.LAST_30_DAYS,
                        null, null, List.of());
                if (item.expected().equals("UNSUPPORTED")) {
                    assertThatThrownBy(() -> planner.resolve(request))
                            .isInstanceOf(ApiException.class)
                            .extracting(exception -> ((ApiException) exception).code())
                            .isEqualTo("BUSINESS_ANALYSIS_UNSUPPORTED");
                } else {
                    assertThat(planner.resolve(request)).isEqualTo(AnalysisType.valueOf(item.expected()));
                }
            }));
        }
    }

    private EvaluationCase parse(String line) {
        int delimiter = line.lastIndexOf(',');
        return new EvaluationCase(line.substring(0, delimiter), line.substring(delimiter + 1));
    }

    private record EvaluationCase(String prompt, String expected) {
    }
}
