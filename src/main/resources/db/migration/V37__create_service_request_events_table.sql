-- Create table for service request event log
CREATE TABLE IF NOT EXISTS service_request_events (
  id UUID PRIMARY KEY,
  service_request_id UUID NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  performed_by UUID,
  payload VARCHAR(4000)
);

CREATE INDEX IF NOT EXISTS idx_service_request_events_request_id ON service_request_events(service_request_id);
