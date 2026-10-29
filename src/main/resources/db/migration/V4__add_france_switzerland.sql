-- Pays prioritaires du lancement : Sénégal, Guinée, Bénin, RD Congo, Mali (déjà présents), France, Suisse.
-- Les tarifs par défaut restent vides tant que le paiement n'est pas branché (voir docs/tarifs-a-activer.sql).
INSERT INTO countries (code, name_fr, name_en, currency, dial_code, timezone) VALUES
 ('FR', 'France', 'France', 'EUR', '+33', 'Europe/Paris'),
 ('CH', 'Suisse', 'Switzerland', 'CHF', '+41', 'Europe/Zurich');
