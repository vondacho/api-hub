-- Executed once, on first boot, against the POSTGRES_DB created by initdb.
--
-- Strapi owns its schema: it runs its own migrations at startup and creates
-- every table it needs. Nothing here may pre-create application tables — this
-- file only sets up what Strapi cannot set up for itself.

-- Strapi stores and compares timestamps in UTC. Pinning the database default
-- stops a differently configured host from shifting createdAt/updatedAt.
ALTER DATABASE strapi SET timezone TO 'UTC';

-- Trigram support, for substring searches over spec titles and descriptions.
-- Extensions can only be created by a superuser, which is why this belongs in
-- the init hook rather than in anything Strapi runs.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- POSTGRES_USER already owns the database and therefore holds CREATE on the
-- public schema. Stated explicitly because PostgreSQL 15 revoked that grant
-- from PUBLIC, and this is the first thing to check if Strapi's bootstrap ever
-- fails with "permission denied for schema public".
GRANT ALL ON SCHEMA public TO strapi;
