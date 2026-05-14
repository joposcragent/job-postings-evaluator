DROP SCHEMA IF EXISTS job_postings CASCADE;

CREATE SCHEMA job_postings;

CREATE TYPE job_postings.evaluation_status AS ENUM (
    'NEW',
    'PENDING',
    'IRRELEVANT',
    'RELEVANT'
);

CREATE TYPE job_postings.response_status AS ENUM (
    'NEW',
    'NOT_INTERESTED',
    'RESPONDED',
    'REJECTED'
);

CREATE TABLE job_postings.postings
(
    uuid              uuid PRIMARY KEY,
    uid               varchar                        NOT NULL,
    title             varchar                        NOT NULL,
    url               varchar                        NOT NULL,
    content           varchar,
    content_vector    real[],
    relevance         real,
    evaluation_status job_postings.evaluation_status DEFAULT 'NEW'::job_postings.evaluation_status NOT NULL,
    response_status   job_postings.response_status   DEFAULT 'NEW'::job_postings.response_status NOT NULL,
    publication_date  varchar                        NOT NULL,
    created_at        timestamptz                    NOT NULL DEFAULT now(),
    updated_at        timestamptz,
    company           varchar,
    notes             text,
    search_query_uuid uuid NOT NULL
);
