package com.smartbanking.service;

import com.smartbanking.dto.AlertReviewRequest;
import com.smartbanking.dto.FraudAlertDto;
import com.smartbanking.dto.PageResponse;
import com.smartbanking.entity.AuditEventType;
import com.smartbanking.entity.FraudAlert;
import com.smartbanking.entity.ReviewStatus;
import com.smartbanking.entity.Severity;
import com.smartbanking.entity.Transfer;
import com.smartbanking.exception.ApiException;
import com.smartbanking.repository.FraudAlertRepository;
import com.smartbanking.security.CurrentUser;
import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Analysts see and review every alert. Customers see (read-only) alerts raised on transfers
 * sent from their own accounts.
 */
@Service
public class FraudAlertService {

    private final FraudAlertRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    public FraudAlertService(FraudAlertRepository repository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    public record Filter(ReviewStatus status, Severity severity, String ruleCode, String accountReference) {
    }

    @Transactional(readOnly = true)
    public PageResponse<FraudAlertDto> search(CurrentUser user, Filter filter, int page, int size) {
        if (page < 0 || size < 1 || size > AccountService.MAX_PAGE_SIZE) {
            throw ApiException.badRequest("INVALID_PAGINATION",
                    "page must be >= 0 and size must be between 1 and " + AccountService.MAX_PAGE_SIZE + ".");
        }
        Specification<FraudAlert> spec = visibleTo(user)
                .and(equalTo("reviewStatus", filter.status()))
                .and(equalTo("severity", filter.severity()))
                .and(equalTo("ruleCode", blankToNull(filter.ruleCode())))
                .and(involvesAccount(blankToNull(filter.accountReference())));
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(repository.findAll(spec, pageable).map(DtoMapper::toDto));
    }

    @Transactional
    public FraudAlertDto review(CurrentUser analyst, UUID alertId, AlertReviewRequest request) {
        if (request.decision() == ReviewStatus.OPEN) {
            throw ApiException.badRequest("INVALID_REVIEW_DECISION", "decision must be CONFIRMED or DISMISSED.");
        }
        FraudAlert alert = repository.findById(alertId)
                .orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "Alert not found."));
        if (alert.getReviewStatus() != ReviewStatus.OPEN) {
            throw ApiException.conflict("ALERT_ALREADY_REVIEWED", "This alert has already been reviewed.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        alert.review(request.decision(), note, analyst.id(), clock.instant());
        auditService.record(analyst.id(), AuditEventType.FRAUD_ALERT_REVIEWED, "alert:" + alert.getId());
        return DtoMapper.toDto(alert);
    }

    private static Specification<FraudAlert> visibleTo(CurrentUser user) {
        if (user.isAnalyst()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("transfer").get("sourceAccount").get("owner").get("id"), user.id());
    }

    private static Specification<FraudAlert> equalTo(String attribute, Object value) {
        return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(root.get(attribute), value);
    }

    private static Specification<FraudAlert> involvesAccount(String reference) {
        return (root, query, cb) -> {
            if (reference == null) {
                return cb.conjunction();
            }
            Join<FraudAlert, Transfer> transfer = root.join("transfer");
            return cb.or(
                    cb.equal(transfer.get("sourceAccount").get("reference"), reference),
                    cb.equal(transfer.get("destinationAccount").get("reference"), reference));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
