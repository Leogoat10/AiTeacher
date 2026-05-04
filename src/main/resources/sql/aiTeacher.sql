-- ============================================
-- AiTeacher 初始化 SQL（精简版）
-- 仅包含：建库、基础建表、基础初始化数据
-- ============================================

CREATE DATABASE IF NOT EXISTS aiteacher DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE aiteacher;

-- ============================================
-- 1) 主数据
-- ============================================
CREATE TABLE IF NOT EXISTS teachers (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    teacher_name VARCHAR(50)  NOT NULL,
    teacher_id   INT          NOT NULL,
    password     VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_teacher_id (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS students (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    student_name VARCHAR(50)  NOT NULL,
    student_id   INT          NOT NULL,
    password     VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_student_id (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS courses (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    course_name VARCHAR(100) NOT NULL,
    course_code VARCHAR(50)  NOT NULL,
    teacher_id  INT          NOT NULL,
    UNIQUE KEY uq_course_code (course_code),
    KEY idx_courses_teacher_id (teacher_id),
    CONSTRAINT fk_course_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversations (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    teacher_id INT                                 NOT NULL,
    title      VARCHAR(255)                        NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    KEY idx_conversations_teacher_id (teacher_id),
    CONSTRAINT fk_conversations_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS messages (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    conversation_id    INT                                 NOT NULL,
    question           TEXT                                NOT NULL,
    answer             TEXT                                NOT NULL,
    user_prompt        TEXT                                NULL COMMENT '用户真实请求 Prompt',
    raw_model_response LONGTEXT                            NULL COMMENT '模型原始返回',
    structured_status  VARCHAR(50)                         NULL COMMENT '结构化处理状态',
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_messages_conversation_id (conversation_id),
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_students (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    course_code VARCHAR(50) NOT NULL,
    student_id  INT         NOT NULL,
    UNIQUE KEY uq_course_student (course_code, student_id),
    KEY idx_course_students_student_id (student_id),
    CONSTRAINT fk_cs_course
        FOREIGN KEY (course_code) REFERENCES courses (course_code)
            ON DELETE CASCADE,
    CONSTRAINT fk_cs_student
        FOREIGN KEY (student_id) REFERENCES students (student_id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 2) 作业与作答
-- ============================================
CREATE TABLE IF NOT EXISTS assignments (
    id                     INT AUTO_INCREMENT PRIMARY KEY,
    message_id             INT                                 NULL COMMENT '关联消息ID（可选）',
    teacher_id             INT                                 NOT NULL COMMENT '发送题目的教师ID',
    course_code            VARCHAR(50)                         NOT NULL COMMENT '发送到的课程代码',
    title                  VARCHAR(255)                        NOT NULL COMMENT '题目标题',
    content                TEXT                                NOT NULL COMMENT '题目内容',
    total_score            INT                                 NULL COMMENT '题目总分',
    question_structure_json LONGTEXT                           NULL COMMENT '结构化题目与分值JSON',
    send_batch_id          VARCHAR(64)                         NULL COMMENT '发送批次ID',
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL COMMENT '发送时间',
    KEY idx_assignments_course (course_code),
    KEY idx_assignments_teacher (teacher_id),
    KEY idx_assignments_send_batch (send_batch_id),
    CONSTRAINT fk_assignment_message
        FOREIGN KEY (message_id) REFERENCES messages (id)
            ON DELETE SET NULL,
    CONSTRAINT fk_assignment_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_assignment_course
        FOREIGN KEY (course_code) REFERENCES courses (course_code)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_assignments (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    assignment_id INT                                 NOT NULL COMMENT '题目ID',
    student_id    INT                                 NOT NULL COMMENT '学生ID',
    received_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL COMMENT '接收时间',
    is_read       TINYINT(1) DEFAULT 0                NULL COMMENT '是否已读',
    UNIQUE KEY uq_student_assignment (assignment_id, student_id),
    KEY idx_student_assignments_student (student_id),
    KEY idx_student_assignments_assignment (assignment_id),
    CONSTRAINT fk_sa_assignment
        FOREIGN KEY (assignment_id) REFERENCES assignments (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_sa_student
        FOREIGN KEY (student_id) REFERENCES students (student_id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_answers (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    assignment_id        INT                                 NOT NULL COMMENT '题目ID',
    student_id           INT                                 NOT NULL COMMENT '学生ID',
    student_answer       TEXT                                NOT NULL COMMENT '学生答案',
    ai_score             VARCHAR(100)                        NULL COMMENT 'AI评分',
    ai_analysis          TEXT                                NULL COMMENT 'AI分析',
    grading_status       VARCHAR(20) DEFAULT 'PENDING'       NULL COMMENT '判题状态：PENDING/RUNNING/SUCCESS/FAILED',
    grading_error        VARCHAR(500)                        NULL COMMENT '判题失败原因',
    model_name           VARCHAR(100)                        NULL COMMENT '判题模型名称',
    prompt_version       VARCHAR(50)                         NULL COMMENT '判题提示词版本',
    raw_response         LONGTEXT                            NULL COMMENT '判题模型原始响应',
    grading_started_at   TIMESTAMP                           NULL COMMENT '判题开始时间',
    grading_completed_at TIMESTAMP                           NULL COMMENT '判题完成时间',
    evaluation_json      LONGTEXT                            NULL COMMENT '结构化判题结果(JSON)',
    submitted_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL COMMENT '提交时间',
    UNIQUE KEY uq_student_answer (assignment_id, student_id),
    KEY idx_student_answers_student (student_id),
    KEY idx_student_answers_assignment (assignment_id),
    KEY idx_student_answers_grading_status (grading_status),
    CONSTRAINT fk_answer_assignment
        FOREIGN KEY (assignment_id) REFERENCES assignments (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_answer_student
        FOREIGN KEY (student_id) REFERENCES students (student_id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS grading_tasks (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    answer_id     INT                                 NOT NULL COMMENT '关联 student_answers.id',
    status        VARCHAR(20)                         NOT NULL COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED',
    retry_count   INT       DEFAULT 0                 NOT NULL COMMENT '重试次数',
    next_retry_at TIMESTAMP                           NULL COMMENT '下次重试时间',
    last_error    VARCHAR(500)                        NULL COMMENT '最后失败原因',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    completed_at  TIMESTAMP                           NULL,
    KEY idx_grading_tasks_status (status),
    CONSTRAINT fk_grading_task_answer
        FOREIGN KEY (answer_id) REFERENCES student_answers (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 3) AI 出题
-- ============================================
CREATE TABLE IF NOT EXISTS generation_tasks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id      INT                                 NOT NULL COMMENT '教师ID',
    conversation_id INT                                 NOT NULL COMMENT '会话ID',
    status          VARCHAR(50)                         NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/COMPLETED_WITH_WARNINGS',
    subject         VARCHAR(100)                        NOT NULL COMMENT '科目',
    grade           VARCHAR(100)                        NOT NULL COMMENT '年级',
    difficulty      VARCHAR(50)                         NOT NULL COMMENT '难度',
    question_type   VARCHAR(50)                         NOT NULL COMMENT '题型',
    question_count  INT                                 NOT NULL COMMENT '题量',
    custom_message  TEXT                                NULL COMMENT '自定义要求',
    request_prompt  TEXT                                NULL COMMENT '最终请求Prompt',
    raw_response    LONGTEXT                            NULL COMMENT '模型原始返回',
    result_json     LONGTEXT                            NULL COMMENT '结构化结果(JSON)',
    error_message   VARCHAR(500)                        NULL COMMENT '失败原因',
    quality_passed  TINYINT(1) DEFAULT 0                NOT NULL COMMENT '质量校验是否通过',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP                           NULL,
    KEY idx_generation_tasks_teacher (teacher_id),
    KEY idx_generation_tasks_conversation (conversation_id),
    KEY idx_generation_tasks_status (status),
    CONSTRAINT fk_gt_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_gt_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 4) AI 教案
-- ============================================
CREATE TABLE IF NOT EXISTS lesson_plan_tasks (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id         INT                                 NOT NULL COMMENT '教师ID',
    conversation_id    INT                                 NULL COMMENT '会话ID',
    status             VARCHAR(20)                         NOT NULL COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED',
    subject            VARCHAR(100)                        NOT NULL COMMENT '科目',
    grade              VARCHAR(100)                        NOT NULL COMMENT '年级',
    teaching_topic     VARCHAR(255)                        NOT NULL COMMENT '课题',
    duration_minutes   INT                                 NOT NULL COMMENT '课时长度(分钟)',
    interaction_count  INT       DEFAULT 3                 NOT NULL COMMENT '最少互动环节数',
    context_used       TINYINT(1) DEFAULT 0                NOT NULL COMMENT '是否启用上下文',
    context_rounds     INT       DEFAULT 5                 NOT NULL COMMENT '关联上下文轮次',
    custom_requirement TEXT                                NULL COMMENT '补充要求',
    request_prompt     LONGTEXT                            NULL COMMENT '生成请求Prompt',
    raw_response       LONGTEXT                            NULL COMMENT '模型原始响应',
    result_json        LONGTEXT                            NULL COMMENT '结构化结果(JSON)',
    error_message      VARCHAR(500)                        NULL COMMENT '失败原因',
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    completed_at       TIMESTAMP                           NULL,
    KEY idx_lesson_plan_tasks_teacher (teacher_id),
    KEY idx_lesson_plan_tasks_status (status),
    KEY idx_lesson_plan_tasks_conversation (conversation_id),
    CONSTRAINT fk_lpt_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_lpt_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lesson_plans (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id                BIGINT                               NULL COMMENT '来源任务ID',
    teacher_id             INT                                  NOT NULL COMMENT '教师ID',
    subject                VARCHAR(100)                         NOT NULL COMMENT '科目',
    grade                  VARCHAR(100)                         NOT NULL COMMENT '年级',
    teaching_topic         VARCHAR(255)                         NOT NULL COMMENT '课题',
    duration_minutes       INT                                  NOT NULL COMMENT '课时长度(分钟)',
    interaction_count      INT                                  NOT NULL COMMENT '互动环节数',
    title                  VARCHAR(255)                         NOT NULL COMMENT '教案标题',
    overview               TEXT                                 NULL COMMENT '教学概述',
    objectives_json        LONGTEXT                             NULL COMMENT '教学目标(JSON数组)',
    key_points_json        LONGTEXT                             NULL COMMENT '教学重点(JSON数组)',
    difficulty_points_json LONGTEXT                             NULL COMMENT '教学难点(JSON数组)',
    teaching_process_json  LONGTEXT                             NULL COMMENT '教学过程(JSON数组)',
    homework               TEXT                                 NULL COMMENT '作业设计',
    assessment             TEXT                                 NULL COMMENT '评价方式',
    extensions             TEXT                                 NULL COMMENT '拓展建议',
    markdown_content       LONGTEXT                             NULL COMMENT '教案Markdown内容',
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP  NULL,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP  NULL ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_lesson_plans_teacher (teacher_id),
    KEY idx_lesson_plans_topic (teaching_topic),
    CONSTRAINT fk_lp_task
        FOREIGN KEY (task_id) REFERENCES lesson_plan_tasks (id)
            ON DELETE SET NULL,
    CONSTRAINT fk_lp_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lesson_plan_prompt_presets (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id        INT                                 NULL COMMENT '创建教师ID，系统默认预设为空',
    title             VARCHAR(100)                        NOT NULL COMMENT '预设名称',
    prompt_content    TEXT                                NOT NULL COMMENT '预设Prompt内容',
    is_system_default TINYINT(1) DEFAULT 0               NOT NULL COMMENT '是否系统默认预设',
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_lpp_teacher (teacher_id),
    KEY idx_lpp_system_default (is_system_default),
    CONSTRAINT fk_lpp_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 5) 学情分析
-- ============================================
CREATE TABLE IF NOT EXISTS learning_analysis_logs (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id            INT                                 NOT NULL COMMENT '教师ID',
    course_code           VARCHAR(50)                         NOT NULL COMMENT '课程代码',
    assignment_id         INT                                 NULL COMMENT '作业ID(为空表示课程整体分析)',
    total_students        INT                                 NOT NULL COMMENT '纳入分析学生数',
    total_answers         INT                                 NOT NULL COMMENT '纳入分析答题数',
    avg_score             DECIMAL(6,2)                        NOT NULL COMMENT '课程平均分',
    mastery_level         VARCHAR(20)                         NOT NULL COMMENT '总体掌握层级',
    mastery_rate          DECIMAL(6,2)                        NOT NULL COMMENT '达标率(%)',
    risk_student_count    INT                                 NOT NULL COMMENT '风险学生数',
    knowledge_points_json LONGTEXT                            NULL COMMENT '薄弱知识点快照(JSON)',
    student_snapshot_json LONGTEXT                            NULL COMMENT '学生画像快照(JSON)',
    analysis_summary      TEXT                                NULL COMMENT '分析摘要',
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    KEY idx_learning_analysis_teacher_course (teacher_id, course_code),
    KEY idx_learning_analysis_assignment (assignment_id),
    KEY idx_learning_analysis_created_at (created_at),
    CONSTRAINT fk_learning_analysis_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_learning_analysis_assignment
        FOREIGN KEY (assignment_id) REFERENCES assignments (id)
            ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_assignment_analyses (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id         INT                                 NOT NULL COMMENT '教师ID',
    course_code        VARCHAR(50)                         NOT NULL COMMENT '课程代码',
    assignment_id      INT                                 NOT NULL COMMENT '作业ID',
    student_id         INT                                 NOT NULL COMMENT '学生ID',
    answer_count       INT                                 NOT NULL COMMENT '该次分析纳入答题数',
    avg_score          DECIMAL(6,2)                        NOT NULL COMMENT '平均分',
    preparedness_score DECIMAL(6,2)                        NOT NULL COMMENT '预备知识评分',
    mastery_level      VARCHAR(20)                         NOT NULL COMMENT '掌握层级',
    recommendation     VARCHAR(500)                        NULL COMMENT '学习建议',
    analysis_json      LONGTEXT                            NULL COMMENT '学生画像JSON',
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_student_assignment_analysis (assignment_id, student_id),
    KEY idx_saa_teacher_course_assignment (teacher_id, course_code, assignment_id),
    CONSTRAINT fk_saa_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_saa_assignment
        FOREIGN KEY (assignment_id) REFERENCES assignments (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_saa_student
        FOREIGN KEY (student_id) REFERENCES students (student_id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS assignment_analysis_snapshots (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id             INT                                 NOT NULL COMMENT '教师ID',
    course_code            VARCHAR(50)                         NOT NULL COMMENT '课程代码',
    assignment_id          INT                                 NOT NULL COMMENT '作业ID',
    assignment_title       VARCHAR(255)                        NOT NULL COMMENT '作业标题',
    overview_json          LONGTEXT                            NULL COMMENT '总览指标JSON',
    distribution_json      LONGTEXT                            NULL COMMENT '分层统计JSON',
    trend_json             LONGTEXT                            NULL COMMENT '趋势数据JSON',
    weak_points_json       LONGTEXT                            NULL COMMENT '薄弱知识点JSON',
    student_profiles_json  LONGTEXT                            NULL COMMENT '学生画像JSON',
    ai_recommendation_json LONGTEXT                            NULL COMMENT 'AI建议JSON',
    summary                TEXT                                NULL COMMENT '分析摘要',
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_assignment_analysis_snapshot (teacher_id, course_code, assignment_id),
    KEY idx_assignment_analysis_assignment (assignment_id),
    CONSTRAINT fk_aas_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_aas_assignment
        FOREIGN KEY (assignment_id) REFERENCES assignments (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 6) 基础初始化数据
-- ============================================
INSERT INTO lesson_plan_prompt_presets (teacher_id, title, prompt_content, is_system_default)
SELECT NULL, '课堂节奏控制版', '加强课堂节奏控制：导入不超过5分钟，核心讲授分段推进，每10分钟加入一次互动检查点，结尾留3分钟课堂小结。', 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM lesson_plan_prompt_presets
    WHERE is_system_default = 1 AND title = '课堂节奏控制版'
);

INSERT INTO lesson_plan_prompt_presets (teacher_id, title, prompt_content, is_system_default)
SELECT NULL, '分层教学加强版', '请设计分层教学方案：同一环节需提供基础任务、进阶任务、挑战任务，并写出针对学困生与资优生的具体指导语。', 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM lesson_plan_prompt_presets
    WHERE is_system_default = 1 AND title = '分层教学加强版'
);

INSERT INTO lesson_plan_prompt_presets (teacher_id, title, prompt_content, is_system_default)
SELECT NULL, '探究互动优先版', '请将教学过程设计为探究驱动：至少包含3次小组协作或同伴讨论，明确每次互动的目标、流程、教师追问与预期产出。', 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM lesson_plan_prompt_presets
    WHERE is_system_default = 1 AND title = '探究互动优先版'
);

INSERT INTO lesson_plan_prompt_presets (teacher_id, title, prompt_content, is_system_default)
SELECT NULL, '考试导向巩固版', '请强化考试能力训练：突出高频考点、易错点和答题规范，每个关键环节加入1个即时检测问题并附纠错建议。', 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM lesson_plan_prompt_presets
    WHERE is_system_default = 1 AND title = '考试导向巩固版'
);
