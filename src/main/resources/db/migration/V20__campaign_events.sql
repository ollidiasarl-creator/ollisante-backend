-- Affiches et campagnes : scans (visites) et inscriptions par source, pour comparer les versions (test A/B).
-- Aucune donnée personnelle : seulement la source, le type d'événement et la date.
create table campaign_events (
    id         bigserial primary key,
    source     varchar(40) not null,
    kind       varchar(10) not null check (kind in ('VISIT', 'SIGNUP')),
    created_at timestamptz not null default now()
);
create index campaign_events_source_idx on campaign_events (source, kind);
