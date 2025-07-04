-- Create user_profiles table with foreign key to users
CREATE TABLE user_profiles
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    bio           TEXT,
    avatar_url    VARCHAR(500),
    date_of_birth DATE,
    phone_number  VARCHAR(20),
    country_code  VARCHAR(3),
    timezone      VARCHAR(50),
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key constraint
    CONSTRAINT fk_user_profiles_user_id
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

-- Create unique constraint ensuring one profile per user
ALTER TABLE user_profiles
    ADD CONSTRAINT uk_user_profiles_user_id UNIQUE (user_id);

-- Create index on user_id for better performance
CREATE INDEX idx_user_profiles_user_id ON user_profiles (user_id);
CREATE INDEX idx_user_profiles_country ON user_profiles (country_code);
