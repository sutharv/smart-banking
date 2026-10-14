package com.smartbanking.controller;

import com.smartbanking.dto.ReconciliationReport;
import com.smartbanking.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation")
@Tag(name = "Reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(summary = "Check balances against the ledger (analysts only)")
    public ReconciliationReport reconcile() {
        return reconciliationService.reconcile();
    }
}
