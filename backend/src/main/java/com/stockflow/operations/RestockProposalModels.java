package com.stockflow.operations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RestockProposalModels {
    private RestockProposalModels() {}

    public record GenerateRequest(
            @NotBlank @Size(max = 1000) String question,
            @NotNull UUID sourceReportId,
            @NotNull UUID recommendationId) {}

    public record VersionCommand(@NotNull Long expectedVersion) {}

    public record DecisionCommand(
            @NotNull Long expectedVersion,
            @Size(max = 500) String reason) {}

    public record ExecuteCommand(
            @NotNull Boolean received,
            @NotNull Long expectedVersion) {}

    public record ProposalResponse(
            UUID id,
            UUID productId,
            String sku,
            String productName,
            RestockProposalStatus status,
            long proposedQuantity,
            String rationale,
            List<String> assumptions,
            UUID sourceReportId,
            int sourceReportVersion,
            UUID recommendationId,
            UUID evidenceId,
            long snapshotAvailable,
            long snapshotReserved,
            long snapshotReorderPoint,
            long snapshotInventoryVersion,
            Instant snapshotInventoryUpdatedAt,
            Instant sourceDataAsOf,
            UUID createdByUserId,
            UUID approvedByUserId,
            Instant approvedAt,
            UUID executionCommandId,
            UUID inventoryAdjustmentId,
            Instant executedAt,
            String failureCode,
            String staleReason,
            String modelProvider,
            String modelName,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        public ProposalResponse {
            assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        }
    }

    public record ProposalList(List<ProposalResponse> items) {
        public ProposalList { items = items == null ? List.of() : List.copyOf(items); }
    }

    public record EventResponse(
            UUID id, RestockProposalStatus fromStatus, RestockProposalStatus toStatus,
            String action, UUID actorUserId, String reasonCode, Instant createdAt) {}

    public record AttemptResponse(
            UUID id, UUID commandId, String status, int attemptNumber, UUID inventoryAdjustmentId,
            String errorCode, Instant startedAt, Instant completedAt) {}

    public record HistoryResponse(List<EventResponse> events, List<AttemptResponse> attempts) {}
}
