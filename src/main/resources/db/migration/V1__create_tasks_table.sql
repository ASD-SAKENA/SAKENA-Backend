CREATE TABLE tasks (
    id          UUID         NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_tasks PRIMARY KEY (id)
);

CREATE INDEX idx_tasks_status ON tasks (status);
