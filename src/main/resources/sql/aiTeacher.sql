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






