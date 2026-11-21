CREATE TABLE admin_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id UUID REFERENCES users(id) ON DELETE SET NULL,
    sender_name VARCHAR(201) NOT NULL,
    body VARCHAR(2000) NOT NULL CHECK (length(trim(body)) > 0),
    request_id UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    recipient_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (sender_id, request_id)
);

CREATE TABLE student_messages (
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL REFERENCES admin_messages(id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ,
    notified_at TIMESTAMPTZ,
    PRIMARY KEY (student_id, message_id)
);
CREATE INDEX ix_student_messages_unread ON student_messages(student_id)
    WHERE read_at IS NULL;
CREATE INDEX ix_student_messages_pending ON student_messages(student_id, message_id)
    WHERE notified_at IS NULL AND read_at IS NULL;
