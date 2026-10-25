package com.stockflow.operations;

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
@RequestMapping("/api/restock-proposals")
class RestockProposalController {
    private final StoreContextProvider contexts;
    private final RestockProposalService service;
    RestockProposalController(StoreContextProvider contexts, RestockProposalService service) {
        this.contexts = contexts; this.service = service;
    }

    @PostMapping("/generate")
    RestockProposalModels.ProposalList generate(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RestockProposalModels.GenerateRequest request) {
        return service.generate(contexts.current(), key, request);
    }
    @GetMapping
    RestockProposalModels.ProposalList list(@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return service.list(contexts.current(), limit);
    }
    @GetMapping("/{id}") RestockProposalModels.ProposalResponse get(@PathVariable UUID id) {
        return service.get(contexts.current(), id);
    }
    @PostMapping("/{id}/approve") RestockProposalModels.ProposalResponse approve(
            @PathVariable UUID id, @Valid @RequestBody RestockProposalModels.VersionCommand command) {
        return service.approve(contexts.current(), id, command.expectedVersion());
    }
    @PostMapping("/{id}/reject") RestockProposalModels.ProposalResponse reject(
            @PathVariable UUID id, @Valid @RequestBody RestockProposalModels.DecisionCommand command) {
        return service.reject(contexts.current(), id, command.expectedVersion(), command.reason());
    }
    @PostMapping("/{id}/cancel") RestockProposalModels.ProposalResponse cancel(
            @PathVariable UUID id, @Valid @RequestBody RestockProposalModels.DecisionCommand command) {
        return service.cancel(contexts.current(), id, command.expectedVersion(), command.reason());
    }
    @PostMapping("/{id}/execute") RestockProposalModels.ProposalResponse execute(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID commandId,
            @Valid @RequestBody RestockProposalModels.ExecuteCommand command) {
        return service.execute(contexts.current(), id, commandId, command);
    }
    @GetMapping("/{id}/history") RestockProposalModels.HistoryResponse history(@PathVariable UUID id) {
        return service.history(contexts.current(), id);
    }
}
