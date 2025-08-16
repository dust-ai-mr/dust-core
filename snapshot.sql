CREATE TABLE IF NOT EXISTS public.snapshot
(
    persistence_id character varying(512) COLLATE pg_catalog."default" NOT NULL,
    ts bigint NOT NULL,
    data bytea NOT NULL,
    CONSTRAINT snapshot_pkey PRIMARY KEY (persistence_id)
)