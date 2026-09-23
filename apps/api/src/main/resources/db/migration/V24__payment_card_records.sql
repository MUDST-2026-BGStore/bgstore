-- Card charges join the bogus gateway's simulated card system. Only the
-- detected card brand and the number's last four digits are recorded; the full
-- card number, security code and expiry never reach the database. V23 declared
-- the payment method check inline, so Postgres auto-named it
-- payment_method_check and it must be dropped before 'Card' can join the list;
-- the reservation's check is explicitly named in V22.
ALTER TABLE payment DROP CONSTRAINT payment_method_check;

ALTER TABLE payment
    ADD CONSTRAINT payment_method_check
    CHECK (method IN ('Cash', 'PromptPay', 'BankTransfer', 'Card', 'Waived'));

ALTER TABLE reservation DROP CONSTRAINT ck_reservation_payment_method;

ALTER TABLE reservation
    ADD CONSTRAINT ck_reservation_payment_method
    CHECK (payment_method IS NULL
        OR payment_method IN ('Cash', 'PromptPay', 'BankTransfer', 'Card', 'Waived'));

ALTER TABLE payment
    ADD COLUMN card_brand VARCHAR(32),
    ADD COLUMN card_last4 VARCHAR(4);
