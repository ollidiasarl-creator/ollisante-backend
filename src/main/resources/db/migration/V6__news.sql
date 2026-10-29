-- Fil d'actualités santé publié par l'administration (épidémies, vaccination, sensibilisation...).
CREATE TABLE news (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title          VARCHAR(200) NOT NULL,
    body           TEXT,
    image_url      VARCHAR(500),
    link_url       VARCHAR(500),
    video_url      VARCHAR(500),
    country_code   VARCHAR(2)   REFERENCES countries (code), -- NULL = toute l'Afrique
    audience       VARCHAR(20)  NOT NULL DEFAULT 'ALL' CHECK (audience IN ('ALL', 'PATIENT', 'PRACTITIONER')),
    author_user_id UUID         REFERENCES users (id) ON DELETE SET NULL,
    published_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_news_published ON news (published_at DESC);
