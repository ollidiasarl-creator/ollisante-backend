-- Structures (personnes morales) : hôpital, clinique, cabinet, centre de santé, association, ONG…
-- Elles gèrent leurs médecins, leurs horaires, leurs rendez-vous et reçoivent les reversements.
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('PATIENT', 'PRACTITIONER', 'ADMIN', 'STRUCTURE'));

CREATE TABLE organizations (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    type                VARCHAR(20)  NOT NULL
                        CHECK (type IN ('HOSPITAL', 'CLINIC', 'MEDICAL_PRACTICE', 'HEALTH_CENTER', 'ASSOCIATION', 'NGO', 'OTHER')),
    name                VARCHAR(200) NOT NULL,
    country_code        VARCHAR(2)   NOT NULL REFERENCES countries (code),
    city                VARCHAR(100),
    address             VARCHAR(255),
    email               VARCHAR(255) NOT NULL,
    phone               VARCHAR(20)  NOT NULL,
    domain              VARCHAR(255),
    registration_number VARCHAR(100) NOT NULL, -- RCCM, NINEA, NIF, SIRET…
    contact_name        VARCHAR(200) NOT NULL,
    contact_role        VARCHAR(100),
    beds                INTEGER      CHECK (beds IS NULL OR beds >= 0),
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    commission_percent  INTEGER      CHECK (commission_percent IS NULL OR commission_percent BETWEEN 0 AND 100),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Sites (établissements) d'une structure ; les établissements créés par des médecins restent sans structure.
ALTER TABLE facilities ADD COLUMN organization_id UUID REFERENCES organizations (id);
CREATE INDEX idx_facilities_organization ON facilities (organization_id);

-- Médecins rattachés à une structure (invitation acceptée par le médecin).
CREATE TABLE organization_members (
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    practitioner_id UUID        NOT NULL REFERENCES practitioners (id) ON DELETE CASCADE,
    status          VARCHAR(10) NOT NULL DEFAULT 'INVITED' CHECK (status IN ('INVITED', 'ACTIVE')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, practitioner_id)
);
CREATE INDEX idx_org_members_practitioner ON organization_members (practitioner_id);

-- Rendez-vous pris dans une structure : figé à la réservation (reversement et commission de la structure).
ALTER TABLE appointments ADD COLUMN organization_id UUID REFERENCES organizations (id);
CREATE INDEX idx_appointments_organization ON appointments (organization_id, starts_at);

-- Reversements : au praticien indépendant OU à la structure.
ALTER TABLE payouts ALTER COLUMN practitioner_id DROP NOT NULL;
ALTER TABLE payouts ADD COLUMN organization_id UUID REFERENCES organizations (id);
ALTER TABLE payouts ADD COLUMN commission_percent INTEGER;
ALTER TABLE payouts ADD CONSTRAINT payouts_beneficiary_chk CHECK ((practitioner_id IS NULL) <> (organization_id IS NULL));
