-- Isolated local test cluster ONLY. Never run this bootstrap in Supabase.
DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon NOLOGIN; END IF; IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated NOLOGIN; END IF; END $$;
CREATE SCHEMA auth;
CREATE TABLE auth.users(id uuid PRIMARY KEY);
CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
GRANT USAGE ON SCHEMA auth,public TO authenticated;
GRANT EXECUTE ON FUNCTION auth.uid() TO authenticated;
INSERT INTO auth.users VALUES('ac3815de-2753-497c-8e9f-02e56f034bd6'),('bc3815de-2753-497c-8e9f-02e56f034bd6');
