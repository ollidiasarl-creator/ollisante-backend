-- Rappels de rendez-vous déjà envoyés : une ligne par rappel (rendez-vous + type, ou récapitulatif + jour),
-- pour ne jamais envoyer deux fois le même message, même si plusieurs serveurs tournent.
CREATE TABLE reminder_log (
    reminder_key VARCHAR(160) PRIMARY KEY,
    sent_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
