-- Lutte contre les faux comptes : pièces justificatives (privées) et signalements des profils par le public.

CREATE TABLE verification_documents (
    id           UUID PRIMARY KEY,
    owner_type   VARCHAR(20)  NOT NULL, -- PRACTITIONER ou ORGANIZATION
    owner_id     UUID         NOT NULL,
    kind         VARCHAR(30)  NOT NULL, -- ORDER_CARD, ID_CARD, DIPLOMA, RCCM, AUTHORIZATION, OTHER
    file_name    VARCHAR(80)  NOT NULL, -- nom généré sur le disque (jamais celui envoyé)
    content_type VARCHAR(60)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_verification_documents_owner ON verification_documents (owner_type, owner_id);

CREATE TABLE profile_reports (
    id               UUID PRIMARY KEY,
    target_type      VARCHAR(20)   NOT NULL, -- PRACTITIONER ou FACILITY
    target_id        UUID          NOT NULL,
    reason           VARCHAR(30)   NOT NULL,
    message          VARCHAR(1000),
    reporter_user_id UUID,
    reporter_ip      VARCHAR(64),
    status           VARCHAR(20)   NOT NULL DEFAULT 'OPEN', -- OPEN ou CLOSED
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ
);
CREATE INDEX idx_profile_reports_target ON profile_reports (target_type, target_id, status);

-- Masqué du public après plusieurs signalements, en attendant la vérification de l'équipe.
ALTER TABLE practitioners ADD COLUMN hidden_by_reports BOOLEAN NOT NULL DEFAULT false;
