create type job_postings.evaluation_status as enum (
    'NEW',
    'PENDING',
    'IRRELEVANT',
    'RELEVANT'
    );
