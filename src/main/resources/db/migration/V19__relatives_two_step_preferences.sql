-- Compte : proches (enfants, parents…) pour qui l'on prend rendez-vous, double authentification au choix
-- et préférences de rappel (e-mail, SMS).
CREATE TABLE relatives (
    id            UUID PRIMARY KEY,
    owner_user_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    birth_date    DATE,
    sex           VARCHAR(10),
    relation      VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX relatives_owner_idx ON relatives (owner_user_id);

-- Rendez-vous pris pour un proche : le nom est gardé tel qu'au moment de la réservation.
ALTER TABLE appointments ADD COLUMN relative_id UUID REFERENCES relatives (id) ON DELETE SET NULL;
ALTER TABLE appointments ADD COLUMN relative_name VARCHAR(200);

ALTER TABLE users ADD COLUMN two_step_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN notify_email BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN notify_sms BOOLEAN NOT NULL DEFAULT TRUE;
