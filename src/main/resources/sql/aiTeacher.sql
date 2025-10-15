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



