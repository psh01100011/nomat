DROP TEMPORARY TABLE IF EXISTS dev_seed_youtube_sources;
CREATE TEMPORARY TABLE dev_seed_youtube_sources (
    sequence_no INT PRIMARY KEY,
    source_url VARCHAR(500) NOT NULL,
    start_time_ms INT NOT NULL,
    end_time_ms INT NOT NULL
);

INSERT INTO dev_seed_youtube_sources (sequence_no, source_url, start_time_ms, end_time_ms)
VALUES
    (1, 'https://www.youtube.com/watch?v=-iExvJ3t6eI', 0, 30000),
    (2, 'https://www.youtube.com/watch?v=2a9Zr4gAcps', 0, 3000),
    (3, 'https://www.youtube.com/watch?v=54AqpXrAdyY', 0, 30000),
    (4, 'https://www.youtube.com/watch?v=5idegokMEMU', 0, 30000),
    (5, 'https://www.youtube.com/watch?v=6iUOds20rSg', 0, 30000),
    (6, 'https://www.youtube.com/watch?v=7KPCDAzAR2Y', 0, 8000),
    (7, 'https://www.youtube.com/watch?v=957bfO80Ny4', 0, 30000),
    (8, 'https://www.youtube.com/watch?v=AY9lBvlGprs', 24000, 70000),
    (9, 'https://www.youtube.com/watch?v=C9GgiqYwI_I', 31000, 60000),
    (10, 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 0, 30000),
    (11, 'https://www.youtube.com/watch?v=E7KCE2Mf1Ig', 0, 30000),
    (12, 'https://www.youtube.com/watch?v=gmbR2xUHjRw', 0, 30000),
    (13, 'https://www.youtube.com/watch?v=icPCOKwCBQM', 30000, 56000),
    (14, 'https://www.youtube.com/watch?v=QIkGEmdfNks', 0, 30000),
    (15, 'https://www.youtube.com/watch?v=RqZd3UbXIr8', 0, 30000),
    (16, 'https://www.youtube.com/watch?v=W_QQ2VF1b4Y', 0, 30000),
    (17, 'https://www.youtube.com/watch?v=YuHVYnX_xA0', 56000, 67000),
    (18, 'https://www.youtube.com/watch?v=Z-59M7yTRYE', 0, 30000);

DROP PROCEDURE IF EXISTS dev_seed_requested_youtube_drafts;
DROP PROCEDURE IF EXISTS dev_seed_youtube_draft;
DELIMITER //

CREATE PROCEDURE dev_seed_youtube_draft(
    IN p_creator_id BIGINT,
    IN p_category_id BIGINT,
    IN p_question_count INT,
    IN p_marker VARCHAR(100),
    IN p_title VARCHAR(100)
)
BEGIN
    DECLARE v_map_id BIGINT;
    DECLARE v_question_id BIGINT;
    DECLARE v_sequence INT DEFAULT 1;
    DECLARE v_source_count INT;
    DECLARE v_source_url VARCHAR(500);
    DECLARE v_start_time_ms INT;
    DECLARE v_end_time_ms INT;
    DECLARE v_conflicting_map_count INT;

    SELECT COUNT(*)
    INTO v_conflicting_map_count
    FROM maps
    WHERE creator_id = p_creator_id
      AND description = p_marker
      AND status NOT IN ('DRAFT', 'DELETED');

    IF v_conflicting_map_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'seed map is no longer a draft; change its description or delete it before reseeding';
    END IF;

    DELETE processing_job
    FROM audio_processing_jobs processing_job
    JOIN question_media media ON media.id = processing_job.question_media_id
    JOIN questions question ON question.id = media.question_id
    JOIN maps map ON map.id = question.map_id
    WHERE map.creator_id = p_creator_id
      AND map.description = p_marker
      AND map.status = 'DRAFT';

    DELETE media
    FROM question_media media
    JOIN questions question ON question.id = media.question_id
    JOIN maps map ON map.id = question.map_id
    WHERE map.creator_id = p_creator_id
      AND map.description = p_marker
      AND map.status = 'DRAFT';

    DELETE answer
    FROM question_answers answer
    JOIN questions question ON question.id = answer.question_id
    JOIN maps map ON map.id = question.map_id
    WHERE map.creator_id = p_creator_id
      AND map.description = p_marker
      AND map.status = 'DRAFT';

    DELETE question
    FROM questions question
    JOIN maps map ON map.id = question.map_id
    WHERE map.creator_id = p_creator_id
      AND map.description = p_marker
      AND map.status = 'DRAFT';

    DELETE FROM maps
    WHERE creator_id = p_creator_id
      AND description = p_marker
      AND status = 'DRAFT';

    INSERT INTO maps (
        comment_count,
        created_at,
        deleted_at,
        description,
        favorite_count,
        like_count,
        play_count,
        published_at,
        question_count,
        question_type,
        status,
        title,
        updated_at,
        version,
        visibility,
        category_id,
        creator_id,
        thumbnail_asset_id
    ) VALUES (
        0,
        NOW(6),
        NULL,
        p_marker,
        0,
        0,
        0,
        NULL,
        p_question_count,
        'AUDIO',
        'DRAFT',
        p_title,
        NOW(6),
        1,
        'PRIVATE',
        p_category_id,
        p_creator_id,
        NULL
    );
    SET v_map_id = LAST_INSERT_ID();

    SELECT COUNT(*) INTO v_source_count FROM dev_seed_youtube_sources;

    WHILE v_sequence <= p_question_count DO
        INSERT INTO questions (
            created_at,
            deleted_at,
            prompt_text,
            question_order,
            status,
            updated_at,
            map_id
        ) VALUES (
            NOW(6),
            NULL,
            CONCAT('테스트 문제 ', LPAD(v_sequence, 3, '0')),
            v_sequence,
            'ACTIVE',
            NOW(6),
            v_map_id
        );
        SET v_question_id = LAST_INSERT_ID();

        INSERT INTO question_answers (
            answer_key,
            answer_text,
            created_at,
            is_primary,
            question_id
        ) VALUES (
            CONCAT('정답', LPAD(v_sequence, 3, '0')),
            CONCAT('정답', LPAD(v_sequence, 3, '0')),
            NOW(6),
            b'1',
            v_question_id
        );

        SELECT source_url, start_time_ms, end_time_ms
        INTO v_source_url, v_start_time_ms, v_end_time_ms
        FROM dev_seed_youtube_sources
        WHERE sequence_no = MOD(v_sequence - 1, v_source_count) + 1;

        INSERT INTO question_media (
            created_at,
            duration_ms,
            end_time_ms,
            failure_message,
            processing_status,
            source_type,
            source_url,
            start_time_ms,
            updated_at,
            asset_id,
            question_id,
            failure_code,
            processing_requested_at
        ) VALUES (
            NOW(6),
            v_end_time_ms - v_start_time_ms,
            v_end_time_ms,
            NULL,
            'QUEUED',
            'YOUTUBE',
            v_source_url,
            v_start_time_ms,
            NOW(6),
            NULL,
            v_question_id,
            NULL,
            NOW(6)
        );

        SET v_sequence = v_sequence + 1;
    END WHILE;
END//

CREATE PROCEDURE dev_seed_requested_youtube_drafts()
BEGIN
    DECLARE v_creator_id BIGINT;
    DECLARE v_category_id BIGINT;

    SELECT id
    INTO v_creator_id
    FROM users
    WHERE login_id = @seed_login_id
      AND status = 'ACTIVE'
    LIMIT 1;

    IF v_creator_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'active seed user was not found';
    END IF;

    SELECT id
    INTO v_category_id
    FROM categories
    WHERE active = b'1'
    ORDER BY id
    LIMIT 1;

    IF v_category_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'active category was not found';
    END IF;

    CALL dev_seed_youtube_draft(
        v_creator_id,
        v_category_id,
        @seed_first_question_count,
        CONCAT('DEV_SEED:YOUTUBE_DRAFT:', @seed_first_question_count),
        CONCAT('[테스트] 유튜브 ', @seed_first_question_count, '문제 임시저장')
    );
    CALL dev_seed_youtube_draft(
        v_creator_id,
        v_category_id,
        @seed_second_question_count,
        CONCAT('DEV_SEED:YOUTUBE_DRAFT:', @seed_second_question_count),
        CONCAT('[테스트] 유튜브 ', @seed_second_question_count, '문제 임시저장')
    );
END//

DELIMITER ;

START TRANSACTION;
CALL dev_seed_requested_youtube_drafts();
COMMIT;

SELECT
    map.id AS map_id,
    map.title,
    map.status,
    map.question_count,
    COUNT(media.id) AS youtube_media_count
FROM maps map
JOIN users creator ON creator.id = map.creator_id
LEFT JOIN questions question
    ON question.map_id = map.id
   AND question.status = 'ACTIVE'
LEFT JOIN question_media media
    ON media.question_id = question.id
   AND media.source_type = 'YOUTUBE'
WHERE creator.login_id = @seed_login_id
  AND map.description IN (
      CONCAT('DEV_SEED:YOUTUBE_DRAFT:', @seed_first_question_count),
      CONCAT('DEV_SEED:YOUTUBE_DRAFT:', @seed_second_question_count)
  )
  AND map.status = 'DRAFT'
GROUP BY map.id, map.title, map.status, map.question_count
ORDER BY map.question_count;

DROP PROCEDURE dev_seed_requested_youtube_drafts;
DROP PROCEDURE dev_seed_youtube_draft;
DROP TEMPORARY TABLE dev_seed_youtube_sources;
