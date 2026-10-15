-- PharmaSafe Database Initialization Script
-- Usage via psql (execute as postgres administrator):
--   psql -U postgres -h localhost -f scripts/init-db.sql
--
-- NOTE: If running manually, replace 'YOUR_APPLICATION_PASSWORD_HERE' with your secure password.
-- When run via scripts/provision-db.ps1, the role and password are configured interactively.

-- ============================================================================
-- STEP 1: Create dedicated application role (Least-Privilege)
-- ============================================================================
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE rolname = 'pharmasafe_user') THEN

      -- The role is created with least privilege (no superuser, no createdb, no createrole).
      CREATE ROLE pharmasafe_user WITH
         LOGIN
         NOSUPERUSER
         NOCREATEDB
         NOCREATEROLE
         NOINHERIT
         NOREPLICATION
         PASSWORD 'YOUR_APPLICATION_PASSWORD_HERE';
      
      RAISE NOTICE 'Role pharmasafe_user created successfully with least privilege.';
   ELSE
      RAISE NOTICE 'Role pharmasafe_user already exists; preserving existing role.';
   END IF;
END
$do$;

-- ============================================================================
-- STEP 2: Create dedicated database with UTF-8 encoding owned by pharmasafe_user
-- Role creation must precede database ownership assignment.
-- In psql, \gexec dynamically executes the generated CREATE statement if not exists.
-- ============================================================================
SELECT 'CREATE DATABASE pharmasafe_db OWNER pharmasafe_user ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'pharmasafe_db')\gexec

-- ============================================================================
-- STEP 3: Switch connection to the dedicated pharmasafe_db
-- Schema and default privilege grants must be applied inside pharmasafe_db.
-- ============================================================================
\connect pharmasafe_db

-- ============================================================================
-- STEP 4: Apply database and schema-level permissions
-- ============================================================================
GRANT ALL PRIVILEGES ON DATABASE pharmasafe_db TO pharmasafe_user;

-- Grant schema permissions inside pharmasafe_db
GRANT USAGE, CREATE ON SCHEMA public TO pharmasafe_user;

-- Grant permissions on any existing tables and sequences
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO pharmasafe_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO pharmasafe_user;

-- Ensure future objects created in schema public are accessible to pharmasafe_user
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO pharmasafe_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO pharmasafe_user;

-- Verify connection and ownership
SELECT current_database() AS active_database, current_user AS active_user;
