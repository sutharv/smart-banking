package com.smartbanking.controller;

import com.smartbanking.dto.AccountDto;
import com.smartbanking.dto.PageResponse;
import com.smartbanking.dto.TransactionDto;
import com.smartbanking.security.CurrentUser;
import com.smartbanking.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List the accounts the caller may view")
    public List<AccountDto> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.listAccounts(CurrentUser.from(jwt));
    }

    @GetMapping("/{reference}")
    @Operation(summary = "Account details by public account reference")
    public AccountDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        return accountService.getAccount(CurrentUser.from(jwt), reference);
    }

    @GetMapping("/{reference}/transactions")
    @Operation(summary = "Account history, newest first. Includes rejected outgoing attempts.")
    public PageResponse<TransactionDto> transactions(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable String reference,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return accountService.getTransactions(CurrentUser.from(jwt), reference, page, size);
    }
}
