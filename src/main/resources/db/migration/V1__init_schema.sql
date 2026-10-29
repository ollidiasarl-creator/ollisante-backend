-- Ollisante : schéma initial. Les montants sont stockés en unité minimale de la devise
-- (XOF, GNF, XAF, CDF : unité entière ; MAD, TND... : centimes).

CREATE TABLE countries (
    code              VARCHAR(2)   PRIMARY KEY,
    name_fr           VARCHAR(100) NOT NULL,
    name_en           VARCHAR(100) NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    dial_code         VARCHAR(6)   NOT NULL,
    default_fee_minor BIGINT
);

CREATE TABLE specialties (
    id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code    VARCHAR(50)  NOT NULL UNIQUE,
    name_fr VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL
);

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE,
    phone         VARCHAR(20)  UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('PATIENT', 'PRACTITIONER', 'ADMIN')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    locale        VARCHAR(5)   NOT NULL DEFAULT 'fr',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_identifier_chk CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE patients (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    birth_date   DATE,
    sex          VARCHAR(10)  CHECK (sex IN ('FEMALE', 'MALE', 'OTHER')),
    country_code VARCHAR(2)   REFERENCES countries (code),
    city         VARCHAR(100),
    address      VARCHAR(255),
    height_cm    INTEGER,
    weight_kg    NUMERIC(5, 2),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE practitioners (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    country_code            VARCHAR(2)   NOT NULL REFERENCES countries (code),
    license_number          VARCHAR(100) NOT NULL,
    bio                     TEXT,
    verification_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                            CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    consultation_fee_minor  BIGINT,
    fee_currency            VARCHAR(3),
    teleconsultation_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_practitioners_country ON practitioners (country_code, verification_status);

CREATE TABLE practitioner_specialties (
    practitioner_id UUID NOT NULL REFERENCES practitioners (id) ON DELETE CASCADE,
    specialty_id    UUID NOT NULL REFERENCES specialties (id),
    PRIMARY KEY (practitioner_id, specialty_id)
);

CREATE TABLE facilities (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(200) NOT NULL,
    country_code VARCHAR(2)   NOT NULL REFERENCES countries (code),
    city         VARCHAR(100),
    address      VARCHAR(255),
    phone        VARCHAR(20),
    latitude     NUMERIC(9, 6),
    longitude    NUMERIC(9, 6),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Plages hebdomadaires récurrentes, à partir desquelles les créneaux réservables sont calculés.
CREATE TABLE availability_rules (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    practitioner_id UUID        NOT NULL REFERENCES practitioners (id) ON DELETE CASCADE,
    facility_id     UUID        REFERENCES facilities (id),
    weekday         INTEGER     NOT NULL CHECK (weekday BETWEEN 1 AND 7), -- 1 = lundi (ISO)
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    slot_minutes    INTEGER     NOT NULL DEFAULT 30 CHECK (slot_minutes BETWEEN 5 AND 240),
    mode            VARCHAR(20) NOT NULL CHECK (mode IN ('IN_PERSON', 'VIDEO')),
    valid_from      DATE        NOT NULL DEFAULT CURRENT_DATE,
    valid_until     DATE,
    CONSTRAINT availability_time_chk CHECK (end_time > start_time)
);
CREATE INDEX idx_availability_practitioner ON availability_rules (practitioner_id);

CREATE TABLE appointments (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    practitioner_id  UUID         NOT NULL REFERENCES practitioners (id),
    patient_id       UUID         NOT NULL REFERENCES patients (id),
    facility_id      UUID         REFERENCES facilities (id),
    mode             VARCHAR(20)  NOT NULL CHECK (mode IN ('IN_PERSON', 'VIDEO')),
    starts_at        TIMESTAMPTZ  NOT NULL,
    ends_at          TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(30)  NOT NULL
                     CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    reason           TEXT,
    video_room       VARCHAR(100),
    cancelled_reason VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT appointments_time_chk CHECK (ends_at > starts_at)
);
CREATE INDEX idx_appointments_patient ON appointments (patient_id, starts_at);
CREATE INDEX idx_appointments_practitioner ON appointments (practitioner_id, starts_at);
-- Empêche deux rendez-vous actifs sur le même créneau pour un praticien (anti double réservation).
CREATE UNIQUE INDEX uq_appointments_active_slot
    ON appointments (practitioner_id, starts_at)
    WHERE status IN ('PENDING_PAYMENT', 'CONFIRMED');

CREATE TABLE payments (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id     UUID        NOT NULL REFERENCES appointments (id),
    payer_user_id      UUID        NOT NULL REFERENCES users (id),
    amount_minor       BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency           VARCHAR(3)  NOT NULL,
    provider           VARCHAR(30) NOT NULL
                       CHECK (provider IN ('ORANGE_MONEY', 'MTN_MOMO', 'WAVE', 'MPESA', 'CARD', 'MANUAL')),
    provider_reference VARCHAR(100),
    status             VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_appointment ON payments (appointment_id);
CREATE UNIQUE INDEX uq_payments_provider_ref
    ON payments (provider, provider_reference) WHERE provider_reference IS NOT NULL;
