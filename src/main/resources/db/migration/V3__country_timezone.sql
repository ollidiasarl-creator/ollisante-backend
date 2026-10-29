-- Les horaires de disponibilité sont saisis en heure locale du pays du praticien.
ALTER TABLE countries ADD COLUMN timezone VARCHAR(40) NOT NULL DEFAULT 'UTC';

UPDATE countries SET timezone = 'Africa/Bamako'       WHERE code = 'ML';
UPDATE countries SET timezone = 'Africa/Dakar'        WHERE code = 'SN';
UPDATE countries SET timezone = 'Africa/Abidjan'      WHERE code = 'CI';
UPDATE countries SET timezone = 'Africa/Ouagadougou'  WHERE code = 'BF';
UPDATE countries SET timezone = 'Africa/Niamey'       WHERE code = 'NE';
UPDATE countries SET timezone = 'Africa/Lome'         WHERE code = 'TG';
UPDATE countries SET timezone = 'Africa/Porto-Novo'   WHERE code = 'BJ';
UPDATE countries SET timezone = 'Africa/Conakry'      WHERE code = 'GN';
UPDATE countries SET timezone = 'Africa/Kinshasa'     WHERE code = 'CD';
UPDATE countries SET timezone = 'Africa/Douala'       WHERE code = 'CM';
UPDATE countries SET timezone = 'Africa/Libreville'   WHERE code = 'GA';
UPDATE countries SET timezone = 'Africa/Brazzaville'  WHERE code = 'CG';
UPDATE countries SET timezone = 'Africa/Casablanca'   WHERE code = 'MA';
UPDATE countries SET timezone = 'Africa/Algiers'      WHERE code = 'DZ';
UPDATE countries SET timezone = 'Africa/Tunis'        WHERE code = 'TN';
UPDATE countries SET timezone = 'Africa/Lagos'        WHERE code = 'NG';
UPDATE countries SET timezone = 'Africa/Accra'        WHERE code = 'GH';
UPDATE countries SET timezone = 'Africa/Nairobi'      WHERE code = 'KE';

ALTER TABLE countries ALTER COLUMN timezone DROP DEFAULT;
