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
create table aiteacher.assignments
(
    id              int auto_increment
        primary key,
    message_id      int                                 null comment '关联的消息ID（可选）',
    teacher_id      int                                 not null comment '发送题目的教师ID',
    course_code     varchar(50)                         not null comment '发送到的课程代码',
    title           varchar(255)                        not null comment '题目标题',
    content         text                                not null comment '题目内容',
    created_at      timestamp default CURRENT_TIMESTAMP null comment '发送时间',
    constraint fk_assignment_message
        foreign key (message_id) references aiteacher.messages (id)
            on delete set null,
    constraint fk_assignment_teacher
        foreign key (teacher_id) references aiteacher.teachers (teacher_id)
            on delete cascade,
    constraint fk_assignment_course
        foreign key (course_code) references aiteacher.courses (course_code)
            on delete cascade
);

create index idx_assignments_course
    on aiteacher.assignments (course_code);

create index idx_assignments_teacher
    on aiteacher.assignments (teacher_id);

-- 学生收到的题目表：记录哪些学生收到了哪些题目
create table aiteacher.student_assignments
(
    id              int auto_increment
        primary key,
    assignment_id   int                                 not null comment '题目ID',
    student_id      int                                 not null comment '学生ID',
    received_at     timestamp default CURRENT_TIMESTAMP null comment '接收时间',
    is_read         tinyint(1) default 0                null comment '是否已读',
    constraint uq_student_assignment
        unique (assignment_id, student_id),
    constraint fk_sa_assignment
        foreign key (assignment_id) references aiteacher.assignments (id)
            on delete cascade,
    constraint fk_sa_student
        foreign key (student_id) references aiteacher.students (student_id)
            on delete cascade
);

create index idx_student_assignments_student
    on aiteacher.student_assignments (student_id);

create index idx_student_assignments_assignment
    on aiteacher.student_assignments (assignment_id);



