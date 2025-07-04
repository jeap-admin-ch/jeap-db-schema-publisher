-- Create user_sessions table with composite primary key
CREATE TABLE user_sessions
(
    user_id       BIGINT                   NOT NULL,
    session_id    VARCHAR(128)             NOT NULL,
    session_token VARCHAR(255)             NOT NULL,
    ip_address    INET,
    user_agent    TEXT,
    is_active     BOOLEAN                  DEFAULT true,
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_accessed TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Composite primary key
    PRIMARY KEY (user_id, session_id),

    -- Foreign key constraint to users table
    CONSTRAINT fk_user_sessions_user_id
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_user_sessions_token ON user_sessions (session_token);
CREATE INDEX idx_user_sessions_expires ON user_sessions (expires_at);
CREATE INDEX idx_user_sessions_active ON user_sessions (is_active);