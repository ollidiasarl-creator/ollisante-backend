-- Formations continues et webinaires pour les professionnels de santé.
CREATE TABLE trainings (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title          VARCHAR(200) NOT NULL,
    description    TEXT,
    format         VARCHAR(20)  NOT NULL CHECK (format IN ('ONLINE', 'IN_PERSON')),
    starts_at      TIMESTAMPTZ  NOT NULL,
    ends_at        TIMESTAMPTZ,
    location       VARCHAR(255),
    link_url       VARCHAR(500),
    country_code   VARCHAR(2)   REFERENCES countries (code), -- NULL = tous les pays
    specialty_code VARCHAR(50)  REFERENCES specialties (code), -- NULL = toutes les spécialités
    published      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT trainings_time_chk CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX idx_trainings_starts ON trainings (starts_at);

-- Questions fréquentes affichées dans l'application.
CREATE TABLE faq_entries (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    question_fr VARCHAR(300) NOT NULL,
    answer_fr   TEXT         NOT NULL,
    question_en VARCHAR(300),
    answer_en   TEXT,
    audience    VARCHAR(20)  NOT NULL DEFAULT 'ALL' CHECK (audience IN ('ALL', 'PATIENT', 'PRACTITIONER')),
    position    INTEGER      NOT NULL DEFAULT 0,
    published   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
