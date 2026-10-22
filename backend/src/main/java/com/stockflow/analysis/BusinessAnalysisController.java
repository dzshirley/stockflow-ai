package com.stockflow.analysis;

import com.stockflow.analysis.BusinessAnalysisModels.AnalysisType;
import com.stockflow.store.StoreContextProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/business-analysis/reports")
class BusinessAnalysisController {

    private final StoreContextProvider contexts;
    private final BusinessReportService reports;

    BusinessAnalysisController(StoreContextProvider contexts, BusinessReportService reports) {
        this.contexts = contexts;
        this.reports = reports;
    }

    @PostMapping
    BusinessAnalysisModels.ReportDetail create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BusinessAnalysisModels.CreateReportRequest request) {
        return reports.create(contexts.current(), idempotencyKey, request);
    }

    @GetMapping
    BusinessAnalysisModels.ReportList list(
            @RequestParam(required = false) BusinessAnalysisReport.Status status,
            @RequestParam(required = false) AnalysisType type,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return reports.list(contexts.current(), status, type, limit);
    }

    @GetMapping("/{reportId}")
    BusinessAnalysisModels.ReportDetail get(@PathVariable UUID reportId) {
        return reports.get(contexts.current(), reportId);
    }
}
