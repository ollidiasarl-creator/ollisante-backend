-- Site d'administration : pays activables et journal des actions des administrateurs.
ALTER TABLE countries ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE admin_audit_log (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id UUID         REFERENCES users (id) ON DELETE SET NULL,
    admin_email   VARCHAR(255),
    action        VARCHAR(60)  NOT NULL,
    target_type   VARCHAR(40),
    target_id     VARCHAR(100),
    details       VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_audit_created ON admin_audit_log (created_at DESC);
