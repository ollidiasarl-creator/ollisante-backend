-- Bouton « Urgence » : numéro d'urgence de chaque pays, saisi et vérifié par l'administrateur.
ALTER TABLE countries ADD COLUMN emergency_number VARCHAR(20);

-- Réglages modifiables depuis le centre d'administration (ex. numéro WhatsApp de l'assistance).
CREATE TABLE app_settings (
    setting_key   VARCHAR(50)  PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
