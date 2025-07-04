-- Add foreign key reference from user_profiles to user_sessions
-- This represents the user's current active session
ALTER TABLE user_profiles
    ADD COLUMN current_session_user_id BIGINT,
    ADD COLUMN current_session_id      VARCHAR(128);

-- Add foreign key constraint to user_sessions table (composite key)
ALTER TABLE user_profiles
    ADD CONSTRAINT fk_user_profiles_current_session
        FOREIGN KEY (current_session_user_id, current_session_id)
            REFERENCES user_sessions (user_id, session_id)
            ON DELETE SET NULL;

-- Create index for better performance
CREATE INDEX idx_user_profiles_current_session ON user_profiles (current_session_user_id, current_session_id);