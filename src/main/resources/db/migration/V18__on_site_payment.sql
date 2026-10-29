-- Moyens de paiement proposés à la réservation, réglés pays par pays dans le centre d'administration :
-- Mobile Money (online_payment) et/ou paiement sur place (on_site_payment). Le patient choisit parmi ceux activés.
ALTER TABLE countries ADD COLUMN on_site_payment BOOLEAN NOT NULL DEFAULT TRUE;
