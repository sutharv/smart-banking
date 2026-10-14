package com.smartbanking.repository;

import com.smartbanking.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByReference(String reference);

    List<Account> findByOwnerIdOrderByReferenceAsc(Long ownerId);

    List<Account> findAllByOrderByReferenceAsc();

    /**
     * Resolves a reference to ids without loading the entity into the persistence context,
     * so that the subsequent locking read returns fresh row state.
     */
    @Query("select new com.smartbanking.repository.AccountKey(a.id, a.owner.id) from Account a where a.reference = :reference")
    Optional<AccountKey> findKeyByReference(@Param("reference") String reference);

    /** SELECT ... FOR UPDATE: blocks other writers of this row until the current transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
