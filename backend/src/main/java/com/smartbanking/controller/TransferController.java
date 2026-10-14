package com.smartbanking.controller;

import com.smartbanking.dto.TransferDto;
import com.smartbanking.dto.TransferRequest;
import com.smartbanking.entity.TransferStatus;
import com.smartbanking.exception.ApiException;
import com.smartbanking.exception.TransferRejectedException;
import com.smartbanking.security.CurrentUser;
import com.smartbanking.service.TransferResult;
import com.smartbanking.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers")
public class TransferController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,80}$");

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * 201 with the transfer when it completed; 422 (problem details with the recorded transfer id)
     * when a business rule rejected it. Repeating a request with the same Idempotency-Key returns
     * the original outcome instead of creating a second transfer.
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Transfer funds between demo accounts")
    public ResponseEntity<TransferDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransferRequest request,
            @Parameter(description = "Client-generated key (8-80 chars: letters, digits, '-', '_') that makes retries safe")
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
        if (idempotencyKey != null && !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 8-80 characters of letters, digits, '-' or '_'.");
        }
        TransferResult result = transferService.createTransfer(CurrentUser.from(jwt), request, idempotencyKey);
        TransferDto transfer = result.transfer();
        if (transfer.status() == TransferStatus.REJECTED) {
            throw new TransferRejectedException(transfer);
        }
        return ResponseEntity.created(URI.create("/api/transfers/" + transfer.id()))
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(transfer);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Transfer details, including any fraud alerts")
    public TransferDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return transferService.getTransfer(CurrentUser.from(jwt), id);
    }
}
