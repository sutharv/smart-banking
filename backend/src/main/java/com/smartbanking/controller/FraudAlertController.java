package com.smartbanking.controller;

import com.smartbanking.dto.AlertReviewRequest;
import com.smartbanking.dto.FraudAlertDto;
import com.smartbanking.dto.PageResponse;
import com.smartbanking.entity.ReviewStatus;
import com.smartbanking.entity.Severity;
import com.smartbanking.security.CurrentUser;
import com.smartbanking.service.FraudAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/fraud-alerts")
@Tag(name = "Fraud alerts")
public class FraudAlertController {

    private final FraudAlertService fraudAlertService;

    public FraudAlertController(FraudAlertService fraudAlertService) {
        this.fraudAlertService = fraudAlertService;
    }

    @GetMapping
    @Operation(summary = "Search alerts. Analysts see all; customers see alerts on their own outgoing transfers.")
    public PageResponse<FraudAlertDto> search(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) ReviewStatus status,
                                              @RequestParam(required = false) Severity severity,
                                              @RequestParam(required = false) String ruleCode,
                                              @RequestParam(required = false) String accountReference,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        var filter = new FraudAlertService.Filter(status, severity, ruleCode, accountReference);
        return fraudAlertService.search(CurrentUser.from(jwt), filter, page, size);
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(summary = "Confirm or dismiss an open alert (analysts only)")
    public FraudAlertDto review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody AlertReviewRequest request) {
        return fraudAlertService.review(CurrentUser.from(jwt), id, request);
    }
}
