-- Suppression de compte à la demande de l'utilisateur : le compte est anonymisé et marqué DELETED
-- (les rendez-vous passés restent dans l'historique du praticien, sans données identifiantes).
ALTER TABLE users DROP CONSTRAINT users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));
