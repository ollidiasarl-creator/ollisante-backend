-- Tarifs communiqués (version du dernier échange). NE PAS EXÉCUTER avant d'avoir branché le paiement (étape 3) :
-- avec un tarif > 0, chaque rendez-vous passe en PENDING_PAYMENT puis est annulé après 30 minutes sans paiement.
-- Montants en unité minimale : XOF, GNF et CDF sans centimes ; EUR, CHF et USD en centimes.

UPDATE countries SET default_fee_minor = 1000  WHERE code IN ('ML', 'SN', 'BJ'); -- 1 000 XOF
UPDATE countries SET default_fee_minor = 1000  WHERE code = 'FR';                -- 10,00 EUR

-- À CONFIRMER avant exécution :
-- Guinée : 50 000 GNF (valeur donnée précédemment, non reconfirmée)
-- UPDATE countries SET default_fee_minor = 50000 WHERE code = 'GN';
-- Suisse : 5 CHF (valeur donnée précédemment, non reconfirmée)
-- UPDATE countries SET default_fee_minor = 500 WHERE code = 'CH';
-- RD Congo : « 20 francs congolais » = moins d'un cent de dollar, probablement une erreur (20 000 CDF ? 20 USD ?)
-- UPDATE countries SET default_fee_minor = 20000 WHERE code = 'CD';                  -- si 20 000 CDF
-- UPDATE countries SET currency = 'USD', default_fee_minor = 2000 WHERE code = 'CD'; -- si 20 USD
