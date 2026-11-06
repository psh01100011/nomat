CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    email VARCHAR(254),
    email_verified_at DATETIME(6),
    profile_image_asset_id BIGINT,
    status ENUM('ACTIVE', 'BLOCKED', 'DELETED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_login_id UNIQUE (login_id),
    CONSTRAINT uk_users_nickname UNIQUE (nickname),
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX idx_users_profile_asset_status (profile_image_asset_id, status)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE assets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uploader_id BIGINT NOT NULL,
    asset_type ENUM('AUDIO', 'IMAGE') NOT NULL,
    original_filename VARCHAR(255),
    storage_key VARCHAR(500) NOT NULL,
    url VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100),
    size_bytes BIGINT,
    duration_ms INT,
    status ENUM('ATTACHED', 'DELETED', 'ORPHANED', 'TEMP') NOT NULL,
    processing_status ENUM('FAILED', 'PENDING', 'PROCESSING', 'READY') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    orphaned_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_assets_status_id (status, id),
    INDEX idx_assets_status_orphaned_id (status, orphaned_at, id),
    CONSTRAINT fk_assets_uploader FOREIGN KEY (uploader_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE users
    ADD CONSTRAINT fk_users_profile_image_asset
        FOREIGN KEY (profile_image_asset_id) REFERENCES assets (id);

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    active BIT NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE maps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    creator_id BIGINT NOT NULL,
    category_id BIGINT,
    thumbnail_asset_id BIGINT,
    question_type ENUM('AUDIO', 'IMAGE', 'TEXT'),
    title VARCHAR(100),
    description TEXT,
    status ENUM('BLOCKED', 'DELETED', 'DRAFT', 'PROCESSING', 'PROCESSING_FAILED', 'PUBLISHED') NOT NULL,
    visibility ENUM('PRIVATE', 'PUBLIC', 'UNLISTED') NOT NULL,
    version INT NOT NULL,
    question_count INT NOT NULL,
    play_count BIGINT NOT NULL,
    like_count BIGINT NOT NULL,
    favorite_count BIGINT NOT NULL,
    comment_count BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_maps_creator_id (creator_id),
    INDEX idx_maps_category_id (category_id),
    INDEX idx_maps_question_type (question_type),
    INDEX idx_maps_status (status),
    INDEX idx_maps_thumbnail_asset_status (thumbnail_asset_id, status),
    INDEX idx_maps_visibility (visibility),
    INDEX idx_maps_created_at (created_at),
    CONSTRAINT fk_maps_creator FOREIGN KEY (creator_id) REFERENCES users (id),
    CONSTRAINT fk_maps_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_maps_thumbnail_asset FOREIGN KEY (thumbnail_asset_id) REFERENCES assets (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    map_id BIGINT NOT NULL,
    question_order INT NOT NULL,
    prompt_text TEXT,
    status ENUM('ACTIVE', 'BLOCKED', 'DELETED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_questions_map_order UNIQUE (map_id, question_order),
    INDEX idx_questions_map_status (map_id, status),
    CONSTRAINT fk_questions_map FOREIGN KEY (map_id) REFERENCES maps (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE question_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    answer_text VARCHAR(255) NOT NULL,
    answer_key VARCHAR(255) NOT NULL,
    is_primary BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_question_answers_question_key UNIQUE (question_id, answer_key),
    INDEX idx_question_answers_question_primary (question_id, is_primary),
    CONSTRAINT fk_question_answers_question FOREIGN KEY (question_id) REFERENCES questions (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE question_media (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    source_type ENUM('TTS', 'UPLOAD', 'YOUTUBE') NOT NULL,
    source_url VARCHAR(500),
    start_time_ms INT,
    end_time_ms INT,
    asset_id BIGINT,
    duration_ms INT,
    processing_status ENUM('FAILED', 'PROCESSING', 'QUEUED', 'READY', 'RETRYING') NOT NULL,
    failure_code ENUM(
        'AUDIO_STREAM_UNAVAILABLE',
        'DOWNLOAD_TEMPORARY_ERROR',
        'INTERNAL_ERROR',
        'INVALID_AUDIO_RANGE',
        'PROCESSING_TIMEOUT',
        'SOURCE_ACCESS_RESTRICTED',
        'SOURCE_UNAVAILABLE',
        'STORAGE_ERROR',
        'TRANSCODING_ERROR',
        'UNKNOWN'
    ),
    failure_message VARCHAR(500),
    processing_requested_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_question_media_question UNIQUE (question_id),
    INDEX idx_question_media_source_type (source_type),
    INDEX idx_question_media_asset_id (asset_id),
    CONSTRAINT fk_question_media_question FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT fk_question_media_asset FOREIGN KEY (asset_id) REFERENCES assets (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE audio_processing_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_media_id BIGINT NOT NULL,
    status ENUM('FAILED', 'PROCESSING', 'QUEUED', 'SUCCEEDED') NOT NULL,
    attempt_count INT NOT NULL,
    failure_code ENUM(
        'AUDIO_STREAM_UNAVAILABLE',
        'DOWNLOAD_TEMPORARY_ERROR',
        'INTERNAL_ERROR',
        'INVALID_AUDIO_RANGE',
        'PROCESSING_TIMEOUT',
        'SOURCE_ACCESS_RESTRICTED',
        'SOURCE_UNAVAILABLE',
        'STORAGE_ERROR',
        'TRANSCODING_ERROR',
        'UNKNOWN'
    ),
    failure_message VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    started_at DATETIME(6),
    completed_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_audio_processing_jobs_question_media UNIQUE (question_media_id),
    INDEX idx_audio_processing_jobs_status_created_at (status, created_at),
    INDEX idx_audio_processing_jobs_status_started_at_id (status, started_at, id),
    CONSTRAINT fk_audio_processing_jobs_question_media
        FOREIGN KEY (question_media_id) REFERENCES question_media (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE map_likes (
    map_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (map_id, user_id),
    INDEX idx_map_likes_user_id (user_id),
    CONSTRAINT fk_map_likes_map FOREIGN KEY (map_id) REFERENCES maps (id),
    CONSTRAINT fk_map_likes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE map_favorites (
    map_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (map_id, user_id),
    INDEX idx_map_favorites_user_id (user_id),
    CONSTRAINT fk_map_favorites_map FOREIGN KEY (map_id) REFERENCES maps (id),
    CONSTRAINT fk_map_favorites_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE map_comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    map_id BIGINT NOT NULL,
    writer_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    status ENUM('ACTIVE', 'BLOCKED', 'DELETED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_map_comments_map_created_at (map_id, created_at),
    INDEX idx_map_comments_writer_id (writer_id),
    CONSTRAINT fk_map_comments_map FOREIGN KEY (map_id) REFERENCES maps (id),
    CONSTRAINT fk_map_comments_writer FOREIGN KEY (writer_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type ENUM('MAP', 'MAP_COMMENT', 'USER') NOT NULL,
    target_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(50) NOT NULL,
    description TEXT,
    status ENUM('PENDING', 'REJECTED', 'RESOLVED', 'REVIEWED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_reports_reporter_target UNIQUE (reporter_id, target_type, target_id),
    INDEX idx_reports_target (target_type, target_id),
    INDEX idx_reports_status_created_at (status, created_at),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE game_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_room_id BIGINT NOT NULL,
    map_id BIGINT NOT NULL,
    map_version INT NOT NULL,
    room_title VARCHAR(100),
    room_has_password BIT NOT NULL,
    invite_code VARCHAR(30),
    max_players INT NOT NULL,
    player_count INT NOT NULL,
    selected_question_count INT NOT NULL,
    random_seed VARCHAR(100),
    answer_time_limit_seconds INT NOT NULL,
    time_limit_mode ENUM('AUDIO_LENGTH', 'FIXED') NOT NULL,
    audio_repeat_enabled BIT NOT NULL,
    initial_hint_enabled BIT NOT NULL,
    initial_hint_trigger_seconds INT NOT NULL,
    status ENUM('ABORTED', 'COMPLETED') NOT NULL,
    ended_reason ENUM('ALL_LEFT', 'COMPLETED', 'HOST_ABORTED', 'ROOM_CLOSED', 'SERVER_ERROR'),
    ended_by_user_id BIGINT,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_game_sessions_source_room UNIQUE (source_room_id),
    INDEX idx_game_sessions_map_started_at (map_id, started_at),
    CONSTRAINT fk_game_sessions_map FOREIGN KEY (map_id) REFERENCES maps (id),
    CONSTRAINT fk_game_sessions_ended_by_user FOREIGN KEY (ended_by_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE game_question_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_number INT NOT NULL,
    winner_user_id BIGINT,
    winner_answer VARCHAR(255),
    earned_score INT NOT NULL,
    answered_ms INT,
    ended_reason ENUM('AUDIO_LOAD_FAILED', 'CORRECT_ANSWER', 'SKIPPED', 'TIME_OVER') NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_game_question_results_session_number UNIQUE (game_session_id, question_number),
    CONSTRAINT fk_game_question_results_session FOREIGN KEY (game_session_id) REFERENCES game_sessions (id),
    CONSTRAINT fk_game_question_results_question FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT fk_game_question_results_winner FOREIGN KEY (winner_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE game_player_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL,
    player_rank INT,
    correct_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_game_player_results_session_user UNIQUE (game_session_id, user_id),
    INDEX idx_game_player_results_user_created_at (user_id, created_at),
    CONSTRAINT fk_game_player_results_session FOREIGN KEY (game_session_id) REFERENCES game_sessions (id),
    CONSTRAINT fk_game_player_results_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE map_play_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    map_id BIGINT NOT NULL,
    play_count INT NOT NULL,
    best_score INT NOT NULL,
    last_score INT NOT NULL,
    first_played_at DATETIME(6),
    last_played_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_map_play_histories_user_map UNIQUE (user_id, map_id),
    INDEX idx_map_play_histories_map_id (map_id),
    CONSTRAINT fk_map_play_histories_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_map_play_histories_map FOREIGN KEY (map_id) REFERENCES maps (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
