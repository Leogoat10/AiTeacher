create database aiTeacher;
       use aiTeacher;

create table aiteacher.teachers
(
    id           int auto_increment
        primary key,
    teacher_name varchar(50)  not null,
    teacher_id   int          not null,
    password     varchar(100) not null,
    constraint uq_teacher_id
        unique (teacher_id)
);

create table aiteacher.students
(
    id           int auto_increment
        primary key,
    student_name varchar(50)  not null,
    student_id   int          not null,
    password     varchar(100) not null,
    constraint uq_student_id
        unique (student_id)
);

create table aiteacher.conversations
(
    id         int auto_increment
        primary key,
    teacher_id int                                 not null,
    title      varchar(255)                        not null,
    created_at timestamp default CURRENT_TIMESTAMP null,
    constraint fk_conversations_teacher
        foreign key (teacher_id) references aiteacher.teachers (teacher_id)
);

create index idx_conversations_teacher_id
    on aiteacher.conversations (teacher_id);

create table aiteacher.messages
(
    id              int auto_increment
        primary key,
    conversation_id int                                 not null,
    question        text                                not null,
    answer          text                                not null,
    created_at      timestamp default CURRENT_TIMESTAMP null,
    updated_at      timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint messages_ibfk_1
        foreign key (conversation_id) references aiteacher.conversations (id)
            on delete cascade
);

create table aiteacher.courses
(
    id          int auto_increment
        primary key,
    course_name varchar(100) not null,
    course_code varchar(50)  not null,
    teacher_id  int          not null,
    constraint course_code
        unique (course_code),
    constraint fk_course_teacher
        foreign key (teacher_id) references aiteacher.teachers (teacher_id)
);

create table aiteacher.course_students
(
    id          int auto_increment
        primary key,
    course_code varchar(50) not null,
    student_id  int         not null,
    constraint uq_course_student
        unique (course_code, student_id),
    constraint fk_cs_course
        foreign key (course_code) references aiteacher.courses (course_code)
            on delete cascade,
    constraint fk_cs_student
        foreign key (student_id) references aiteacher.students (student_id)
            on delete cascade
);

-- 题目表：老师从聊天记录中选中并发送的题目
CREATE TABLE IF NOT EXISTS aiteacher.assignments
(
    id              INT AUTO_INCREMENT
        PRIMARY KEY,
    message_id      INT                                 NULL COMMENT '关联的消息ID（可选）',
    teacher_id      INT                                 NOT NULL COMMENT '发送题目的教师ID',
    course_code     VARCHAR(50)                         NOT NULL COMMENT '发送到的课程代码',
    title           VARCHAR(255)                        NOT NULL COMMENT '题目标题',
    content         TEXT                                NOT NULL COMMENT '题目内容',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL COMMENT '发送时间',
    CONSTRAINT fk_assignment_message
        FOREIGN KEY (message_id) REFERENCES aiteacher.messages (id)
            ON DELETE SET NULL,
    CONSTRAINT fk_assignment_teacher
        FOREIGN KEY (teacher_id) REFERENCES aiteacher.teachers (teacher_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_assignment_course
        FOREIGN KEY (course_code) REFERENCES aiteacher.courses (course_code)
            ON DELETE CASCADE
);

CREATE INDEX idx_assignments_course
    ON aiteacher.assignments (course_code);

CREATE INDEX idx_assignments_teacher
    ON aiteacher.assignments (teacher_id);

-- 学生收到的题目表：记录哪些学生收到了哪些题目
CREATE TABLE IF NOT EXISTS aiteacher.student_assignments
(
    id              INT AUTO_INCREMENT
        PRIMARY KEY,
    assignment_id   INT                                 NOT NULL COMMENT '题目ID',
    student_id      INT                                 NOT NULL COMMENT '学生ID',
    received_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL COMMENT '接收时间',
    is_read         TINYINT(1) DEFAULT 0                NULL COMMENT '是否已读',
    CONSTRAINT uq_student_assignment
        UNIQUE (assignment_id, student_id),
    CONSTRAINT fk_sa_assignment
        FOREIGN KEY (assignment_id) REFERENCES aiteacher.assignments (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_sa_student
        FOREIGN KEY (student_id) REFERENCES aiteacher.students (student_id)
            ON DELETE CASCADE
);

CREATE INDEX idx_student_assignments_student
    ON aiteacher.student_assignments (student_id);

CREATE INDEX idx_student_assignments_assignment
    ON aiteacher.student_assignments (assignment_id);

-- 学生答题表：记录学生的答题情况、AI评分和弱点分析
CREATE TABLE IF NOT EXISTS aiteacher.student_answers
(
    id              INT AUTO_INCREMENT
        PRIMARY KEY,
    assignment_id   INT                                 NOT NULL COMMENT '题目ID',
    student_id      INT                                 NOT NULL COMMENT '学生ID',
    student_answer  TEXT                                NOT NULL COMMENT '学生的答案',
    ai_score        VARCHAR(100)                        NULL COMMENT 'AI评分',
    ai_analysis     TEXT                                NULL COMMENT 'AI弱点分析',
    submitted_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL COMMENT '提交时间',
    CONSTRAINT uq_student_answer
        UNIQUE (assignment_id, student_id),
    CONSTRAINT fk_answer_assignment
        FOREIGN KEY (assignment_id) REFERENCES aiteacher.assignments (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_answer_student
        FOREIGN KEY (student_id) REFERENCES aiteacher.students (student_id)
            ON DELETE CASCADE
);

CREATE INDEX idx_student_answers_student
    ON aiteacher.student_answers (student_id);

CREATE INDEX idx_student_answers_assignment
    ON aiteacher.student_answers (assignment_id);


-- =========================
-- Phase 1: AI出题任务化与结构化支持
-- =========================

CREATE TABLE IF NOT EXISTS aiteacher.generation_tasks
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id      INT                                  NOT NULL COMMENT '教师ID',
    conversation_id INT                                  NOT NULL COMMENT '会话ID',
    status          VARCHAR(50)                          NOT NULL COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED/COMPLETED_WITH_WARNINGS',
    subject         VARCHAR(100)                         NOT NULL COMMENT '科目',
    grade           VARCHAR(100)                         NOT NULL COMMENT '年级',
    difficulty      VARCHAR(50)                          NOT NULL COMMENT '难度',
    question_type   VARCHAR(50)                          NOT NULL COMMENT '题型',
    question_count  INT                                  NOT NULL COMMENT '题量',
    custom_message  TEXT                                 NULL COMMENT '自定义要求',
    request_prompt  TEXT                                 NULL COMMENT '最终请求Prompt',
    raw_response    LONGTEXT                             NULL COMMENT '模型原始返回',
    result_json     LONGTEXT                             NULL COMMENT '结构化结果(JSON)',
    error_message   VARCHAR(500)                         NULL COMMENT '失败原因',
    quality_passed  TINYINT(1) DEFAULT 0                NOT NULL COMMENT '质量校验是否通过',
    created_at      TIMESTAMP  DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at      TIMESTAMP  DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP                            NULL,
    CONSTRAINT fk_gt_teacher
        FOREIGN KEY (teacher_id) REFERENCES aiteacher.teachers (teacher_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_gt_conversation
        FOREIGN KEY (conversation_id) REFERENCES aiteacher.conversations (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_generation_tasks_teacher
    ON aiteacher.generation_tasks (teacher_id);

CREATE INDEX idx_generation_tasks_conversation
    ON aiteacher.generation_tasks (conversation_id);

CREATE INDEX idx_generation_tasks_status
    ON aiteacher.generation_tasks (status);

ALTER TABLE aiteacher.messages
    ADD COLUMN user_prompt TEXT NULL COMMENT '用户真实请求 Prompt',
    ADD COLUMN raw_model_response LONGTEXT NULL COMMENT '模型原始返回',
    ADD COLUMN structured_status VARCHAR(50) NULL COMMENT '结构化处理状态';


-- =========================
-- Phase 1（第1周）：发送批次追踪与判题调用治理
-- =========================

-- 1) 题目发送批次ID（用于批次追踪与审计）
ALTER TABLE aiteacher.assignments
    ADD COLUMN send_batch_id VARCHAR(64) NULL COMMENT '发送批次ID';

CREATE INDEX idx_assignments_send_batch
    ON aiteacher.assignments (send_batch_id);

-- 2) 学生答案判题状态字段（第2周异步判题预留）
ALTER TABLE aiteacher.student_answers
    ADD COLUMN grading_status VARCHAR(20) DEFAULT 'SUCCESS' NULL COMMENT '判题状态：PENDING/RUNNING/SUCCESS/FAILED',
    ADD COLUMN grading_error VARCHAR(500) NULL COMMENT '判题失败原因',
    ADD COLUMN model_name VARCHAR(100) NULL COMMENT '判题模型名称',
    ADD COLUMN prompt_version VARCHAR(50) NULL COMMENT '判题提示词版本',
    ADD COLUMN raw_response LONGTEXT NULL COMMENT '判题模型原始响应',
    ADD COLUMN grading_started_at TIMESTAMP NULL COMMENT '判题开始时间',
    ADD COLUMN grading_completed_at TIMESTAMP NULL COMMENT '判题完成时间';

CREATE INDEX idx_student_answers_grading_status
    ON aiteacher.student_answers (grading_status);

-- 3) 判题任务表（第2周异步任务能力预留）
CREATE TABLE IF NOT EXISTS aiteacher.grading_tasks
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    answer_id       INT                                  NOT NULL COMMENT '关联student_answers.id',
    status          VARCHAR(20)                          NOT NULL COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED',
    retry_count     INT         DEFAULT 0                NOT NULL COMMENT '重试次数',
    next_retry_at   TIMESTAMP                            NULL COMMENT '下次重试时间',
    last_error      VARCHAR(500)                         NULL COMMENT '最后失败原因',
    created_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP                            NULL,
    CONSTRAINT fk_grading_task_answer
        FOREIGN KEY (answer_id) REFERENCES aiteacher.student_answers (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_grading_tasks_status
    ON aiteacher.grading_tasks (status);

-- 第2周补充：异步判题模式下默认状态应为 PENDING
ALTER TABLE aiteacher.student_answers
    MODIFY COLUMN grading_status VARCHAR(20) DEFAULT 'PENDING' NULL COMMENT '判题状态：PENDING/RUNNING/SUCCESS/FAILED';

-- 第3周补充：结构化判题结果
ALTER TABLE aiteacher.student_answers
    ADD COLUMN evaluation_json LONGTEXT NULL COMMENT '结构化判题结果(JSON)';


