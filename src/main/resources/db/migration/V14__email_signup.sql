-- Inscription patient par e-mail : le compte n'est créé qu'après saisie du code reçu par e-mail.
-- On garde ici la demande en attente (mot de passe déjà chiffré, code sous forme d'empreinte).
CREATE TABLE email_signups (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    country_code  VARCHAR(2),
    birth_date    DATE,
    locale        VARCHAR(5),
    code_hash     VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    consumed      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_signups_email ON email_signups (email, created_at DESC);
