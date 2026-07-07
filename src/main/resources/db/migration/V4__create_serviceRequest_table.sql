CREATE TABLE IF NOT EXISTS service_requests (
                                                id UUID PRIMARY KEY,
                                                title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    location VARCHAR(255),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    category_group VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    sub_category VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    assigned_to UUID,
    expected_completion_at TIMESTAMP,
    completion_report VARCHAR(4000),
    completion_cost DOUBLE PRECISION,
    resolved_at TIMESTAMP
    );

-- اگر می‌خواهید ایندکس روی created_by داشته باشید (برای جستجوی سریع‌تر)
CREATE INDEX idx_service_requests_created_by ON service_requests(created_by);
