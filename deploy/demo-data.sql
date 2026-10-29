-- Données de démonstration (serveur de test uniquement) : établissements fictifs, présentations des médecins,
-- FAQ et actualités de lancement. Relançable : chaque bloc remplace ce qu'il a lui-même créé.
BEGIN;

-- 1. Établissements : noms inventés, marqués « (démo) » pour ne jamais se confondre avec un établissement réel.
UPDATE facilities SET name = 'Polyclinique Kër Ollisanté (démo)', city = 'Dakar', address = 'Avenue Léopold Sédar Senghor, Plateau'
  WHERE id = '4f20dd58-7fa6-4049-91cb-490946e677f8';
UPDATE facilities SET name = 'Clinique Lagune Santé (démo)', city = 'Abidjan', address = 'Boulevard Latrille, Cocody'
  WHERE id = 'b0c77f95-65b1-44be-855c-48157bb0f343';
UPDATE facilities SET name = 'Centre médical Les Cocotiers (démo)', city = 'Cotonou', address = 'Quartier Cadjèhoun'
  WHERE id = '5a509785-f5ab-40fe-9313-8d95d9086f7a';
UPDATE facilities SET name = 'Clinique Kaloum Horizon (démo)', city = 'Conakry', address = 'Kaloum, centre-ville'
  WHERE id = '98213c65-9954-4f13-9e78-0bdff0ea6896';
UPDATE facilities SET name = 'Centre de santé Gombe Espoir (démo)', city = 'Kinshasa', address = 'Commune de la Gombe'
  WHERE id = 'e7ebbf8e-5d40-4c20-8f5c-dee22716523c';
UPDATE facilities SET name = 'Cabinet médical Seine Santé (démo)', city = 'Paris', address = 'Paris 15e'
  WHERE id = 'b35838ec-93c9-4e52-9f4c-0664810a8135';
UPDATE facilities SET name = 'Centre médical Léman (démo)', city = 'Genève', address = 'Plainpalais'
  WHERE id = '3684a162-8bbe-49bc-a335-0cf0f7acb6c9';

-- 2. Présentation des médecins de test (comptes @test.ollisante.com), selon la spécialité et le pays.
UPDATE practitioners p SET bio = x.bio || ' ' ||
  CASE p.country_code
    WHEN 'SN' THEN 'Langues : français, wolof.'
    WHEN 'CI' THEN 'Langues : français, dioula.'
    WHEN 'BJ' THEN 'Langues : français, fon.'
    WHEN 'GN' THEN 'Langues : français, peul, soussou.'
    WHEN 'CD' THEN 'Langues : français, lingala.'
    WHEN 'CH' THEN 'Langues : français, allemand.'
    ELSE 'Langues : français, anglais.' END
  || ' Profil de démonstration.'
FROM (
  SELECT ps.practitioner_id, CASE s.code
    WHEN 'GENERAL_PRACTICE' THEN 'Médecine générale pour adultes et enfants : consultations, suivi des maladies chroniques, certificats et vaccinations.'
    WHEN 'PEDIATRICS' THEN 'Pédiatre : suivi de la croissance, vaccinations, consultations des nourrissons et des enfants jusqu''à 15 ans.'
    WHEN 'GYNECOLOGY' THEN 'Gynécologue-obstétricienne : suivi de grossesse, contraception, dépistage et consultations gynécologiques.'
    WHEN 'CARDIOLOGY' THEN 'Cardiologue : hypertension, douleurs thoraciques, bilan cardiaque et électrocardiogramme.'
    WHEN 'DERMATOLOGY' THEN 'Dermatologue : affections de la peau, des cheveux et des ongles, dépistage des grains de beauté.'
    WHEN 'OPHTHALMOLOGY' THEN 'Ophtalmologue : contrôle de la vue, prescription de lunettes, suivi du glaucome et du diabète.'
    WHEN 'ENT' THEN 'ORL : oreilles, nez, gorge, sinusites, troubles de l''audition et de la voix.'
    WHEN 'DENTISTRY' THEN 'Chirurgien-dentiste : soins dentaires, détartrage, extractions et prévention pour toute la famille.'
    WHEN 'PSYCHIATRY' THEN 'Psychiatre : troubles anxieux, dépression, troubles du sommeil, accompagnement et suivi.'
    WHEN 'NEUROLOGY' THEN 'Neurologue : maux de tête, épilepsie, vertiges, troubles de la mémoire et suivi après un AVC.'
    WHEN 'ORTHOPEDICS' THEN 'Orthopédiste : douleurs articulaires et du dos, traumatismes, suivi après une fracture.'
    WHEN 'UROLOGY' THEN 'Urologue : troubles urinaires, prostate, calculs rénaux.'
    WHEN 'NUTRITION' THEN 'Nutritionniste : diabète, surpoids, alimentation de l''enfant et de la femme enceinte.'
    WHEN 'RADIOLOGY' THEN 'Radiologue : interprétation des examens d''imagerie et conseils avant un examen.'
    WHEN 'ANESTHESIOLOGY' THEN 'Anesthésiste-réanimatrice : consultation pré-anesthésique avant une intervention.'
    ELSE 'Consultations sur rendez-vous.' END AS bio
  FROM practitioner_specialties ps JOIN specialties s ON s.id = ps.specialty_id
) x, users u
WHERE x.practitioner_id = p.id AND u.id = p.user_id AND u.email LIKE '%@test.ollisante.com';

-- 3. FAQ : on remplace uniquement les questions ci-dessous (les autres, ajoutées depuis l'administration, restent).
CREATE TEMP TABLE demo_faq (question_fr varchar, answer_fr text, audience varchar, position int) ON COMMIT DROP;
INSERT INTO demo_faq (question_fr, answer_fr, audience, position) VALUES
('Comment prendre rendez-vous ?', 'Choisissez votre pays, une structure de santé et une spécialité, puis un créneau libre dans l''agenda du professionnel. Le prix de la consultation est affiché avant de confirmer.', 'PATIENT', 1),
('Comment payer ma consultation ?', 'Selon le pays, vous payez en ligne par Mobile Money au moment de la réservation (Orange Money, MTN, Moov, Free, Airtel…) ou directement sur place. Le mode de paiement est indiqué avant de confirmer.', 'PATIENT', 2),
('Mon créneau est-il réservé pendant le paiement ?', 'Oui. Le créneau vous est réservé 30 minutes, le temps de valider le paiement sur votre téléphone. Sans paiement dans ce délai, il est libéré pour un autre patient.', 'PATIENT', 3),
('Puis-je annuler un rendez-vous ?', 'Oui, depuis « Mes rendez-vous ». Plus de 24 heures avant : remboursement complet. Entre 24 heures et 6 heures avant : remboursement de 50 %. Moins de 6 heures avant ou absence : pas de remboursement. Si le professionnel annule, vous êtes remboursé en totalité.', 'PATIENT', 4),
('Quand suis-je remboursé ?', 'Le remboursement est envoyé automatiquement sur le compte Mobile Money qui a servi au paiement. Le délai de réception dépend de votre opérateur.', 'PATIENT', 5),
('Les médecins sont-ils vérifiés ?', 'Oui. Notre équipe vérifie le numéro d''ordre de chaque professionnel et l''immatriculation de chaque structure avant de les rendre visibles.', 'PATIENT', 6),
('Qui peut voir le motif de ma consultation ?', 'Seulement le professionnel que vous consultez (et, le cas échéant, la structure où il exerce). L''équipe Ollisanté ne le voit pas.', 'PATIENT', 7),
('Que faire en cas d''urgence ?', 'Ollisanté n''est pas un service d''urgence. En cas d''urgence, appelez immédiatement le numéro d''urgence de votre pays ou rendez-vous aux urgences les plus proches. Le bouton « Urgence » de l''application affiche les numéros vérifiés par notre équipe.', 'ALL', 8),
('Comment supprimer mon compte ?', 'Dans l''application : Compte, puis Supprimer mon compte. Annulez d''abord vos rendez-vous déjà payés pour être remboursé.', 'ALL', 9),
('Comment rendre mon profil visible des patients ?', 'Après votre inscription, notre équipe vérifie votre numéro d''ordre. Une fois validé, votre profil et vos créneaux apparaissent dans les recherches.', 'PRACTITIONER', 10),
('Comment ouvrir mes horaires ?', 'Dans l''application : Consultations, puis Mes disponibilités. Ajoutez vos jours, vos heures, la durée des créneaux et le lieu de consultation.', 'PRACTITIONER', 11),
('Comment suis-je payé ?', 'Les paiements en ligne sont encaissés par OLLIDIA et reversés chaque vendredi, déduction faite de la commission (15 % pour un médecin indépendant, 10 % pour une structure ou un médecin rattaché).', 'PRACTITIONER', 12),
('Une structure peut-elle gérer mes horaires ?', 'Oui, si vous acceptez son invitation dans Compte, puis Mes structures. Vous pouvez quitter la structure à tout moment.', 'PRACTITIONER', 13);
DELETE FROM faq_entries WHERE question_fr IN (SELECT question_fr FROM demo_faq);
INSERT INTO faq_entries (question_fr, answer_fr, audience, position) SELECT question_fr, answer_fr, audience, position FROM demo_faq;

-- 4. Actualités de lancement (remplacées si le script est relancé).
DELETE FROM news WHERE title IN ('Ollisanté arrive : prenez rendez-vous en quelques minutes', 'Professionnels de santé : rejoignez Ollisanté');
INSERT INTO news (title, body, audience, published_at) VALUES
('Ollisanté arrive : prenez rendez-vous en quelques minutes', 'Trouvez un médecin ou une structure de santé vérifiés, choisissez votre créneau et payez par Mobile Money ou sur place. Ollisanté est en phase pilote au Sénégal, en Côte d''Ivoire, au Bénin, en Guinée, en RD Congo, en France et en Suisse.', 'ALL', now()),
('Professionnels de santé : rejoignez Ollisanté', 'Gérez vos horaires et vos rendez-vous depuis votre téléphone, recevez vos paiements chaque vendredi. Inscription gratuite, profil vérifié par notre équipe.', 'PRACTITIONER', now() - interval '1 hour');

COMMIT;

SELECT (SELECT count(*) FROM faq_entries) AS faq, (SELECT count(*) FROM news) AS actualites,
       (SELECT count(*) FROM practitioners WHERE bio LIKE '%Profil de démonstration.') AS bios,
       (SELECT count(*) FROM facilities WHERE name LIKE '%(démo)') AS etablissements;
