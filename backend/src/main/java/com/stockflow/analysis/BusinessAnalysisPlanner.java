package com.stockflow.analysis;

import com.stockflow.analysis.BusinessAnalysisModels.AnalysisType;
import com.stockflow.analysis.BusinessAnalysisModels.CreateReportRequest;
import com.stockflow.common.error.ApiExceptions;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
class BusinessAnalysisPlanner {

    AnalysisType resolve(CreateReportRequest request) {
        String question = request.question().toLowerCase(Locale.ROOT);
        if (contains(question, "delete ", "write arbitrary sql", "change every product",
                "forecast tomorrow", "who will buy", "another store", "other store's")) {
            throw ApiExceptions.badRequest(
                    "BUSINESS_ANALYSIS_UNSUPPORTED",
                    "The request is an unsupported prediction, data operation, or business mutation");
        }
        if (request.analysisType() != null && request.analysisType() != AnalysisType.AUTO) {
            return request.analysisType();
        }
        if (contains(question, "what if", "scenario", "if prices", "if unit sales", "if sales",
                "coverage change", "model revenue")) {
            return AnalysisType.SCENARIO_ANALYSIS;
        }
        if (contains(question, "inventory", "stockout", "stock out", "low stock", "excess stock", "run out")) {
            return AnalysisType.INVENTORY_RISK;
        }
        if (contains(question, "revenue", "sales", "profit", "product", "category", "price", "volume",
                "average order")) {
            return AnalysisType.REVENUE_INVESTIGATION;
        }
        throw ApiExceptions.badRequest(
                "BUSINESS_ANALYSIS_UNSUPPORTED",
                "Ask about revenue changes, product/category drivers, inventory risk, or a supported scenario");
    }

    String title(AnalysisType type, String question) {
        String prefix = switch (type) {
            case REVENUE_INVESTIGATION -> "Revenue investigation";
            case INVENTORY_RISK -> "Inventory risk analysis";
            case SCENARIO_ANALYSIS -> "Business scenario";
            case AUTO -> throw new IllegalArgumentException("AUTO must be resolved before creating a report");
        };
        String normalized = question.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 120 ? prefix + ": " + normalized : prefix + ": " + normalized.substring(0, 117) + "...";
    }

    private boolean contains(String value, String... phrases) {
        for (String phrase : phrases) if (value.contains(phrase)) return true;
        return false;
    }
}
