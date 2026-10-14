package com.smartbanking.service;

import com.smartbanking.dto.AccountDto;
import com.smartbanking.dto.PageResponse;
import com.smartbanking.dto.TransactionDto;
import com.smartbanking.entity.Account;
import com.smartbanking.entity.Transfer;
import com.smartbanking.entity.TransferStatus;
import com.smartbanking.exception.ApiException;
import com.smartbanking.repository.AccountRepository;
import com.smartbanking.repository.FraudAlertRepository;
import com.smartbanking.repository.TransferRepository;
import com.smartbanking.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read access to accounts. Customers see only their own accounts; analysts have read-only
 * access to all accounts for investigations. Accounts a caller may not see are reported as
 * "not found" so that their existence is not disclosed.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    public static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final FraudAlertRepository fraudAlertRepository;

    public AccountService(AccountRepository accountRepository, TransferRepository transferRepository,
                          FraudAlertRepository fraudAlertRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.fraudAlertRepository = fraudAlertRepository;
    }

    public List<AccountDto> listAccounts(CurrentUser user) {
        List<Account> accounts = user.isAnalyst()
                ? accountRepository.findAllByOrderByReferenceAsc()
                : accountRepository.findByOwnerIdOrderByReferenceAsc(user.id());
        return accounts.stream().map(DtoMapper::toDto).toList();
    }

    public AccountDto getAccount(CurrentUser user, String reference) {
        return DtoMapper.toDto(findVisibleAccount(user, reference));
    }

    public PageResponse<TransactionDto> getTransactions(CurrentUser user, String reference, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("INVALID_PAGINATION",
                    "page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        Account account = findVisibleAccount(user, reference);
        Page<Transfer> transfers = transferRepository.findHistory(account.getId(), TransferStatus.COMPLETED,
                PageRequest.of(page, size));
        List<UUID> ids = transfers.getContent().stream().map(Transfer::getId).toList();
        Set<UUID> flagged = ids.isEmpty() ? Set.of() : fraudAlertRepository.findFlaggedTransferIds(ids);
        return PageResponse.from(transfers.map(t -> DtoMapper.toTransaction(t, account.getId(), flagged.contains(t.getId()))));
    }

    private Account findVisibleAccount(CurrentUser user, String reference) {
        return accountRepository.findByReference(reference)
                .filter(account -> user.isAnalyst() || account.isOwnedBy(user.id()))
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "Account not found."));
    }
}
