package com.stockflow.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.ai.BusinessAnalysisAiService;
import com.stockflow.analytics.AnalyticsPeriodResolver;
import com.stockflow.analysis.BusinessAnalysisModels.AnalysisType;
import com.stockflow.analysis.BusinessAnalysisModels.CreateReportRequest;
import com.stockflow.analysis.BusinessAnalysisModels.ReportDetail;
import com.stockflow.analysis.BusinessAnalysisModels.ReportList;
import com.stockflow.analysis.BusinessAnalysisModels.ScenarioRequest;
import com.stockflow.analysis.BusinessAnalysisModels.ScenarioType;
import com.stockflow.common.error.ApiException;
import com.stockflow.common.error.ApiExceptions;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class BusinessReportService {

    private static final Pattern PERCENT = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*%");

    private final BusinessAnalysisPlanner planner;
    private final BusinessAnalysisEngine engine;
    private final BusinessReportAssembler assembler;
    private final BusinessAnalysisAiService ai;
    private final BusinessReportPersistenceService persistence;
    private final AnalyticsPeriodResolver periods;
    private final StoreRepository stores;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final Clock clock;

    BusinessReportService(
            BusinessAnalysisPlanner planner,
            BusinessAnalysisEngine engine,
            BusinessReportAssembler assembler,
            BusinessAnalysisAiService ai,
            BusinessReportPersistenceService persistence,
            AnalyticsPeriodResolver periods,
            StoreRepository stores,
            ObjectMapper mapper,
            MeterRegistry meters,
            Clock clock) {
        this.planner = planner;
        this.engine = engine;
        this.assembler = assembler;
        this.ai = ai;
        this.persistence = persistence;
        this.periods = periods;
        this.stores = stores;
        this.mapper = mapper;
        this.meters = meters;
        this.clock = clock;
    }

    public ReportDetail create(StoreContext context, String rawIdempotencyKey, CreateReportRequest rawRequest) {
        var timer = Timer.start(meters);
        String outcome = "failed";
        String key = idempotencyKey(rawIdempotencyKey);
        AnalysisType type = planner.resolve(rawRequest);
        var request = normalized(rawRequest, type);
        var store = stores.findById(context.storeId()).orElseThrow(() -> ApiExceptions.notFound("Store"));
        var current = periods.resolve(request.preset(), request.startDate(), request.endDateExclusive(), store.getTimeZone());
        var previous = periods.previous(current);
        String requestHash = hash(request);
        var started = persistence.begin(
                context, key, requestHash, type, planner.title(type, request.question()),
                current.startDate(), current.endDateExclusive(), previous.startDate(),
                previous.endDateExclusive(), store.getTimeZone());
        if (started.replayed()) {
            outcome = "replayed";
            timer.stop(meters.timer("stockflow.business_analysis.report.duration", "outcome", outcome));
            return get(context, started.reportId());
        }

        try {
            persistence.markRunning(started.reportId());
            var entity = persistence.get(context, started.reportId());
            var bundle = engine.analyze(context, request);
            String evidenceJson = write(assembler.synthesisBundle(type, bundle));
            var synthesis = ai.synthesize(evidenceJson);
            var completedAt = Instant.now(clock);
            var report = assembler.assemble(
                    started.reportId(), context, type, entity.getTitle(), bundle, synthesis,
                    entity.getCreatedAt(), completedAt);
            persistence.complete(started.reportId(), write(report), report.modelMetadata(), report.dataAsOf());
            outcome = "completed";
            return get(context, started.reportId());
        } catch (RuntimeException exception) {
            persistence.fail(started.reportId(), safeCode(exception));
            meters.counter("stockflow.business_analysis.report.failures", "code", safeCode(exception)).increment();
            throw exception;
        } finally {
            timer.stop(meters.timer("stockflow.business_analysis.report.duration", "outcome", outcome));
        }
    }

    public ReportDetail get(StoreContext context, UUID reportId) {
        var entity = persistence.get(context, reportId);
        BusinessAnalysisReport report = null;
        if (entity.getReportDocument() != null) {
            try {
                report = mapper.readValue(entity.getReportDocument(), BusinessAnalysisReport.class);
                BusinessAnalysisValidator.validate(report);
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                throw ApiExceptions.conflict(
                        "BUSINESS_REPORT_CORRUPT", "The persisted business report could not be validated");
            }
        }
        return new ReportDetail(persistence.summary(entity), report);
    }

    public ReportList list(
            StoreContext context,
            BusinessAnalysisReport.Status status,
            AnalysisType type,
            int limit) {
        if (type == AnalysisType.AUTO) {
            throw ApiExceptions.badRequest("INVALID_ANALYSIS_TYPE", "AUTO is not a persisted report type");
        }
        return persistence.list(context, status, type, limit);
    }

    private CreateReportRequest normalized(CreateReportRequest request, AnalysisType type) {
        String question = request.question().strip().replaceAll("\\s+", " ");
        List<ScenarioRequest> scenarios = request.scenarios();
        if (type == AnalysisType.SCENARIO_ANALYSIS && scenarios.isEmpty()) {
            scenarios = List.of(parseScenario(question));
        }
        return new CreateReportRequest(
                question, type, request.preset(), request.startDate(), request.endDateExclusive(), scenarios);
    }

    private ScenarioRequest parseScenario(String question) {
        var matcher = PERCENT.matcher(question);
        if (!matcher.find()) {
            throw ApiExceptions.badRequest(
                    "SCENARIO_PERCENT_REQUIRED", "Scenario questions require an explicit percentage change");
        }
        var percent = new BigDecimal(matcher.group(1));
        String lower = question.toLowerCase(Locale.ROOT);
        if (percent.signum() > 0 && (lower.contains("decrease") || lower.contains("decline")
                || lower.contains("fall") || lower.contains("reduction") || lower.contains("reduce"))) {
            percent = percent.negate();
        }
        ScenarioType type = lower.contains("discount") ? ScenarioType.DISCOUNT_CHANGE
                : lower.contains("price") ? ScenarioType.PRICE_CHANGE
                : lower.contains("coverage") || lower.contains("velocity") ? ScenarioType.SALES_VELOCITY_CHANGE
                : ScenarioType.UNIT_SALES_CHANGE;
        return new ScenarioRequest(type, percent);
    }

    private String idempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw ApiExceptions.badRequest(
                    "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain between 1 and 200 characters");
        }
        return value.strip();
    }

    private String hash(CreateReportRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(write(request).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize business analysis", exception);
        }
    }

    private String safeCode(RuntimeException exception) {
        return exception instanceof ApiException apiException
                ? apiException.code()
                : exception.getClass().getSimpleName();
    }
}
