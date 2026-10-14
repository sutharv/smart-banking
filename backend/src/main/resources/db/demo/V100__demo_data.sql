-- Demo data for local development only (loaded by the "demo" profile).
-- All accounts and people are fictional. Every user's password is: Demo!2026

INSERT INTO app_user (id, username, password_hash, role) VALUES
    (1, 'alice',   '$2a$10$vZ/KtgD856LiACjD8sahpO6FeG4qPhkr3AgEMbL2a3bETkAJW/pwG', 'CUSTOMER'),
    (2, 'bob',     '$2a$10$vZ/KtgD856LiACjD8sahpO6FeG4qPhkr3AgEMbL2a3bETkAJW/pwG', 'CUSTOMER'),
    (3, 'analyst', '$2a$10$vZ/KtgD856LiACjD8sahpO6FeG4qPhkr3AgEMbL2a3bETkAJW/pwG', 'ANALYST');

SELECT setval(pg_get_serial_sequence('app_user', 'id'), 100);

INSERT INTO account (id, owner_user_id, account_reference, display_name, currency, balance, status) VALUES
    (1, 1, 'SB-EUR-0001', 'Alice Everyday', 'EUR', 5000.00,  'ACTIVE'),
    (2, 1, 'SB-EUR-0002', 'Alice Savings',  'EUR', 12000.00, 'ACTIVE'),
    (3, 1, 'SB-PLN-0003', 'Alice Złoty',    'PLN', 8000.00,  'ACTIVE'),
    (4, 2, 'SB-EUR-0004', 'Bob Everyday',   'EUR', 1500.00,  'ACTIVE'),
    (5, 2, 'SB-EUR-0005', 'Bob Old Wallet', 'EUR', 200.00,   'FROZEN');

SELECT setval(pg_get_serial_sequence('account', 'id'), 100);

-- Opening balances are posted to the ledger so that, for every account,
-- balance = SUM(ledger_entry.amount).
INSERT INTO ledger_entry (account_id, amount, entry_type, created_at)
SELECT id, balance, 'OPENING_BALANCE', now() FROM account;
