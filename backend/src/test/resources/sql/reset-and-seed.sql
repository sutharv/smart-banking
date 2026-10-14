-- Resets the database to a known fixture before every integration test.
-- TRUNCATE bypasses the row-level immutability trigger on ledger_entry.
TRUNCATE audit_event, fraud_alert, ledger_entry, transfer, account, app_user RESTART IDENTITY CASCADE;

-- Password for every user: Demo!2026
INSERT INTO app_user (id, username, password_hash, role, enabled) VALUES
    (1, 'alice',    '$2a$10$vZ/KtgD856LiACjD8sahpO6FeG4qPhkr3AgEMbL2a3bETkAJW/pwG', 'CUSTOMER', TRUE),
    (2, 'bob',      '$2a$10$vZ/KtgD856LiACjD8sahpO6FeG4qPhkr3AgEMbL2a3bETkAJW/pwG', 'CUSTOMER', TRUE),
    (3, 'analyst',  '$2a$10$vZ/KtgD856LiACjD8sahpO6FeG4qPhkr3AgEMbL2a3bETkAJW/pwG', 'ANALYST',  TRUE),
    (4, 'disabled', '$2a$10$vZ/KtgD856LiACjD8sahpO6FeG4qPhkr3AgEMbL2a3bETkAJW/pwG', 'CUSTOMER', FALSE);

SELECT setval(pg_get_serial_sequence('app_user', 'id'), 100);

INSERT INTO account (id, owner_user_id, account_reference, display_name, currency, balance, status) VALUES
    (1, 1, 'SB-EUR-0001', 'Alice Everyday', 'EUR', 5000.00,  'ACTIVE'),
    (2, 1, 'SB-EUR-0002', 'Alice Savings',  'EUR', 12000.00, 'ACTIVE'),
    (3, 1, 'SB-PLN-0003', 'Alice Złoty',    'PLN', 8000.00,  'ACTIVE'),
    (4, 2, 'SB-EUR-0004', 'Bob Everyday',   'EUR', 1500.00,  'ACTIVE'),
    (5, 2, 'SB-EUR-0005', 'Bob Old Wallet', 'EUR', 200.00,   'FROZEN');

SELECT setval(pg_get_serial_sequence('account', 'id'), 100);

INSERT INTO ledger_entry (account_id, amount, entry_type, created_at)
SELECT id, balance, 'OPENING_BALANCE', now() FROM account;
