create table job_postings.postings
(
    uuid              uuid                           default gen_random_uuid()                     not null
        constraint postings_pk
            primary key,
    uid               varchar                                                                      not null,
    title             varchar                                                                      not null,
    url               varchar                                                                      not null,
    content           varchar,
    content_vector    real[],
    relevance         real,
    evaluation_status job_postings.evaluation_status default 'NEW'::job_postings.evaluation_status not null,
    response_status   job_postings.response_status   default 'NEW'::job_postings.response_status,
    publication_date  varchar                                                                      not null,
    created_at        timestamp with time zone       default now()                                 not null,
    updated_at        timestamp with time zone,
    company           varchar
);

comment on table job_postings.postings is 'Метаданные вакансий';

comment on column job_postings.postings.uuid is 'Внутренний UUID вакансии, генерируется системой';

comment on column job_postings.postings.uid is 'Уникальный ID вакансии на сайте вакансий';

comment on column job_postings.postings.url is 'URL вакансии на сайте';

comment on column job_postings.postings.content is 'Текст вакансии';

comment on column job_postings.postings.content_vector is 'Векторное представление контента';

comment on column job_postings.postings.evaluation_status is 'Статус автоматического процесса оценки ';

comment on column job_postings.postings.response_status is 'Статус отклика, проставляется вручную';

comment on column job_postings.postings.publication_date is 'Дата публикации на сайте';

comment on column job_postings.postings.created_at is 'Дата создания записи';

comment on column job_postings.postings.company is 'Название компании';

create index postings_uid_index
    on job_postings.postings (uid);

create index postings_company_index
    on job_postings.postings (company);

create index postings_title_index
    on job_postings.postings (title);
