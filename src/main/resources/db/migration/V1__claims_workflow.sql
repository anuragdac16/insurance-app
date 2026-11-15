CREATE TABLE claimants (
    id UUID PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL,
    phone VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_claimants_email_lower ON claimants (lower(email));

CREATE TABLE claims (
    id UUID PRIMARY KEY,
    claim_number VARCHAR(20) NOT NULL UNIQUE,
    tracking_token_hash CHAR(64) NOT NULL,
    claimant_id UUID NOT NULL REFERENCES claimants(id),
    market_code VARCHAR(3) NOT NULL,
    product_type VARCHAR(20) NOT NULL CHECK (product_type IN ('MOTOR', 'PROPERTY')),
    policy_number VARCHAR(80) NOT NULL,
    incident_date DATE NOT NULL,
    incident_location VARCHAR(300),
    incident_description TEXT NOT NULL,
    estimated_loss_amount NUMERIC(15, 2),
    currency_code CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'SUBMITTED', 'UNDER_REVIEW', 'AWAITING_INFORMATION',
        'APPROVED', 'REJECTED', 'SETTLED'
    )),
    assigned_to VARCHAR(160),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_claim_market_code CHECK (market_code ~ '^[A-Z]{2,3}$'),
    CONSTRAINT ck_claim_currency_code CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_claim_estimated_loss CHECK (estimated_loss_amount IS NULL OR estimated_loss_amount >= 0),
    CONSTRAINT ck_incident_not_after_submission CHECK (incident_date <= submitted_at::date)
);

CREATE INDEX ix_claims_queue ON claims (market_code, status, submitted_at);
CREATE INDEX ix_claims_assignee_status ON claims (assigned_to, status) WHERE assigned_to IS NOT NULL;
CREATE INDEX ix_claims_submitted_at ON claims (submitted_at DESC);

CREATE TABLE claim_assignments (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(id),
    staff_id VARCHAR(160) NOT NULL,
    assigned_by VARCHAR(160) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unassigned_at TIMESTAMPTZ
);

CREATE INDEX ix_claim_assignments_claim ON claim_assignments (claim_id, assigned_at DESC);
CREATE UNIQUE INDEX uq_claim_assignment_active ON claim_assignments (claim_id)
    WHERE unassigned_at IS NULL;

CREATE TABLE claim_status_history (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    changed_by VARCHAR(160) NOT NULL,
    reason TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_claim_status_history_claim ON claim_status_history (claim_id, changed_at);

CREATE TABLE claim_documents (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(id),
    uploaded_by VARCHAR(160) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(160) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0),
    sha256 CHAR(64) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_claim_documents_claim ON claim_documents (claim_id, uploaded_at);

CREATE TABLE claim_information_requests (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(id),
    requested_by VARCHAR(160) NOT NULL,
    request_text TEXT NOT NULL,
    due_at TIMESTAMPTZ,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ
);

CREATE INDEX ix_information_requests_open ON claim_information_requests (claim_id, due_at)
    WHERE responded_at IS NULL;

CREATE TABLE claim_information_responses (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES claim_information_requests(id),
    submitted_by VARCHAR(160) NOT NULL,
    response_text TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_information_response_content CHECK (length(trim(coalesce(response_text, ''))) > 0)
);

CREATE UNIQUE INDEX uq_information_response_request ON claim_information_responses (request_id);

CREATE TABLE claim_decisions (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL UNIQUE REFERENCES claims(id),
    outcome VARCHAR(10) NOT NULL CHECK (outcome IN ('APPROVED', 'REJECTED')),
    rationale TEXT NOT NULL,
    liability_amount NUMERIC(15, 2),
    currency_code CHAR(3) NOT NULL,
    decided_by VARCHAR(160) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_decision_liability CHECK (liability_amount IS NULL OR liability_amount >= 0),
    CONSTRAINT ck_decision_currency CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE TABLE claim_settlements (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL UNIQUE REFERENCES claims(id),
    payment_reference VARCHAR(100) NOT NULL UNIQUE,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    currency_code CHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED')),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_at TIMESTAMPTZ
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0 CHECK (publish_attempts >= 0),
    last_error TEXT
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at, id)
    WHERE published_at IS NULL;
