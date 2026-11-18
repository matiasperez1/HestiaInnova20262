CREATE TABLE IF NOT EXISTS demo.call_report (
    user_id uuid NOT NULL REFERENCES demo.identity(user_id),
    number text NOT NULL,
    category text NOT NULL,
    PRIMARY KEY (number, user_id)
);
ALTER TABLE demo.call_report
    DROP CONSTRAINT IF EXISTS call_report_number_check,
    DROP CONSTRAINT IF EXISTS call_report_category_check,
    ADD CONSTRAINT call_report_number_check CHECK (number ~ '^20255501[0-9]{2}$'),
    ADD CONSTRAINT call_report_category_check CHECK (category IN (
        'POSSIBLE_FRAUD','IMPERSONATION','UNWANTED_ADS','HARASSMENT','OTHER_GENERAL',
        'REQUEST_FOR_MONEY','REQUEST_FOR_CODES','OTHER'
    ));
GRANT SELECT, INSERT, UPDATE, DELETE ON demo.call_report TO hestia_backend;
