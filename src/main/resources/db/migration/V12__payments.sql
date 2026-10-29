-- Paiement des consultations (Mobile Money via PawaPay), remboursements et reversements aux praticiens.

-- Pays : paiement en ligne activé ou non (sinon, paiement sur place), et tarifs du lancement.
ALTER TABLE countries ADD COLUMN online_payment BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE countries SET currency = 'USD' WHERE code = 'CD';  -- tarif RDC fixé en dollars (PawaPay : Airtel USD)
UPDATE countries SET default_fee_minor = 500,   online_payment = TRUE WHERE code IN ('SN', 'CI', 'BJ');
UPDATE countries SET default_fee_minor = 1000,  online_payment = TRUE WHERE code = 'CD';  -- 10,00 USD
UPDATE countries SET default_fee_minor = 50000 WHERE code = 'GN';                       -- 50 000 GNF, sur place
UPDATE countries SET default_fee_minor = 1000  WHERE code IN ('FR', 'CH');              -- 10,00 EUR / CHF, sur place

-- Rendez-vous : tarif figé à la réservation et mode de règlement.
ALTER TABLE appointments ADD COLUMN price_minor    BIGINT;
ALTER TABLE appointments ADD COLUMN price_currency VARCHAR(3);
ALTER TABLE appointments ADD COLUMN payment_mode   VARCHAR(10) NOT NULL DEFAULT 'FREE'
    CHECK (payment_mode IN ('ONLINE', 'ON_SITE', 'FREE'));

-- Reversements hebdomadaires (manuels) aux praticiens.
CREATE TABLE payouts (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    practitioner_id  UUID        NOT NULL REFERENCES practitioners (id),
    currency         VARCHAR(3)  NOT NULL,
    gross_minor      BIGINT      NOT NULL, -- encaissé net des remboursements
    commission_minor BIGINT      NOT NULL, -- part OLLIDIA
    amount_minor     BIGINT      NOT NULL, -- à verser au praticien
    period_end       DATE        NOT NULL, -- consultations antérieures à cette date (vendredi)
    status           VARCHAR(10) NOT NULL DEFAULT 'DUE' CHECK (status IN ('DUE', 'PAID')),
    paid_at          TIMESTAMPTZ,
    reference        VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payouts_practitioner ON payouts (practitioner_id, period_end DESC);

-- Paiements (table créée en V1, jamais utilisée jusqu'ici).
ALTER TABLE payments DROP CONSTRAINT payments_provider_check;
ALTER TABLE payments ADD CONSTRAINT payments_provider_check
    CHECK (provider IN ('PAWAPAY', 'ORANGE_MONEY', 'MTN_MOMO', 'WAVE', 'MPESA', 'CARD', 'MANUAL'));
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'PARTIALLY_REFUNDED', 'REFUNDED'));
ALTER TABLE payments ADD COLUMN operator       VARCHAR(40);  -- code opérateur PawaPay, ex. ORANGE_SEN
ALTER TABLE payments ADD COLUMN phone          VARCHAR(20);
ALTER TABLE payments ADD COLUMN refunded_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN failure_code   VARCHAR(60);
ALTER TABLE payments ADD COLUMN payout_id      UUID REFERENCES payouts (id);
-- Un seul paiement réussi par rendez-vous.
CREATE UNIQUE INDEX uq_payments_succeeded_appointment ON payments (appointment_id)
    WHERE status IN ('SUCCEEDED', 'PARTIALLY_REFUNDED', 'REFUNDED');

-- Remboursements (l'identifiant est celui transmis à PawaPay).
CREATE TABLE refunds (
    id           UUID        PRIMARY KEY,
    payment_id   UUID        NOT NULL REFERENCES payments (id),
    amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency     VARCHAR(3)  NOT NULL,
    status       VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    reason       VARCHAR(40) NOT NULL,
    failure_code VARCHAR(60),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refunds_payment ON refunds (payment_id);
