-- Create users table
CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(100) NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    full_name  VARCHAR(200),
    is_active  BOOLEAN                  DEFAULT true,
    score      DECIMAL(12, 2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create index on email for better performance
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_active ON users (is_active);
