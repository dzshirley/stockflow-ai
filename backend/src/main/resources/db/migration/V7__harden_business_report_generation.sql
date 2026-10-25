ALTER TABLE business_analysis_reports
    ADD COLUMN generation_owner UUID;

ALTER TABLE business_analysis_reports
    ADD COLUMN generation_started_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE business_analysis_reports
    ADD COLUMN generation_lease_until TIMESTAMP WITH TIME ZONE;

UPDATE business_analysis_reports
SET status = 'FAILED',
    failure_code = 'REPORT_GENERATION_INTERRUPTED_UPGRADE',
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE status IN ('PENDING', 'RUNNING');

ALTER TABLE business_analysis_reports
    ADD CONSTRAINT ck_business_reports_generation_lease
        CHECK ((status = 'RUNNING'
                    AND generation_owner IS NOT NULL
                    AND generation_started_at IS NOT NULL
                    AND generation_lease_until IS NOT NULL
                    AND generation_lease_until > generation_started_at)
            OR (status <> 'RUNNING'
                    AND generation_owner IS NULL
                    AND generation_started_at IS NULL
                    AND generation_lease_until IS NULL));

CREATE INDEX idx_business_reports_generation_recovery
    ON business_analysis_reports (status, generation_lease_until, updated_at, id);
