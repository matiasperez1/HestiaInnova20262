-- New application objects only. Review against the remote schema before applying.
BEGIN;
DO $$ BEGIN
  IF to_regclass('public.gmail_events') IS NOT NULL OR to_regclass('private.gmail_connections') IS NOT NULL OR to_regclass('private.gmail_devices') IS NOT NULL OR to_regclass('private.gmail_notification_outbox') IS NOT NULL THEN
    RAISE EXCEPTION 'Gmail objects already exist: inspect schema before applying this migration';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='hestia_backend') THEN CREATE ROLE hestia_backend NOLOGIN NOBYPASSRLS; END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS private;
-- Existing schema grants are preserved; new private tables have no client grants.
GRANT USAGE ON SCHEMA private, public TO hestia_backend;
CREATE TABLE private.gmail_connections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  gmail_email text UNIQUE CHECK (gmail_email=lower(gmail_email) AND length(gmail_email)<=254),
  encrypted_refresh_token text, history_id text CHECK(history_id ~ '^[0-9]{1,30}$'), watch_expiration timestamptz,
  time_zone text NOT NULL DEFAULT 'UTC' CHECK(length(time_zone)<=80),
  monitoring_started_at timestamptz, token_created_at timestamptz,
  connection_status text NOT NULL DEFAULT 'PENDING' CHECK(connection_status IN ('PENDING','ACTIVATING','ACTIVE','TEMP_ERROR','REAUTH_REQUIRED','DISCONNECTED','DISCONNECTED_REVOCATION_PENDING')),
  last_sync_at timestamptz, last_error_code text, last_manual_request_at timestamptz,
  lease_token uuid, lease_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.gmail_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  message_id_hash text NOT NULL CHECK(message_id_hash ~ '^[a-f0-9]{64}$'), received_at timestamptz NOT NULL, analyzed_at timestamptz NOT NULL DEFAULT now(),
  risk_level text NOT NULL CHECK(risk_level IN ('LOW','MEDIUM','HIGH')), score smallint CHECK(score BETWEEN 0 AND 100),
  reasons jsonb NOT NULL DEFAULT '[]' CHECK(jsonb_typeof(reasons)='array'), sender text CHECK(char_length(sender)<=160), subject text CHECK(char_length(subject)<=120),
  content_limited boolean NOT NULL DEFAULT false, UNIQUE(user_id,message_id_hash),
  CHECK(risk_level<>'LOW' OR (sender IS NULL AND subject IS NULL AND score IS NULL AND reasons='[]'::jsonb))
);
CREATE INDEX gmail_events_week ON public.gmail_events(user_id,received_at);
CREATE TABLE private.gmail_devices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  installation_id uuid NOT NULL, fcm_token text NOT NULL UNIQUE CHECK(length(fcm_token) BETWEEN 20 AND 4096),
  platform text NOT NULL DEFAULT 'android' CHECK(platform='android'), notifications_enabled boolean NOT NULL DEFAULT false,
  expires_at timestamptz NOT NULL DEFAULT now()+interval '30 days',
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), UNIQUE(user_id,installation_id)
);
CREATE TABLE private.gmail_notification_outbox (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL UNIQUE REFERENCES public.gmail_events(id) ON DELETE CASCADE,
  attempts int NOT NULL DEFAULT 0, delivered_at timestamptz, next_attempt_at timestamptz NOT NULL DEFAULT now(), created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE private.gmail_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.gmail_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.gmail_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.gmail_notification_outbox ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.gmail_connections,private.gmail_devices,private.gmail_notification_outbox FROM PUBLIC,anon,authenticated;
REVOKE ALL ON public.gmail_events FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.gmail_events TO authenticated;
CREATE POLICY gmail_events_owner_read ON public.gmail_events FOR SELECT TO authenticated USING((SELECT auth.uid())=user_id);
CREATE POLICY gmail_connections_backend ON private.gmail_connections TO hestia_backend USING(true) WITH CHECK(true);
CREATE POLICY gmail_devices_backend ON private.gmail_devices TO hestia_backend USING(true) WITH CHECK(true);
CREATE POLICY gmail_events_backend ON public.gmail_events TO hestia_backend USING(true) WITH CHECK(true);
CREATE POLICY gmail_outbox_backend ON private.gmail_notification_outbox TO hestia_backend USING(true) WITH CHECK(true);
GRANT SELECT,INSERT,UPDATE,DELETE ON private.gmail_connections,private.gmail_devices,private.gmail_notification_outbox,public.gmail_events TO hestia_backend;
COMMIT;
