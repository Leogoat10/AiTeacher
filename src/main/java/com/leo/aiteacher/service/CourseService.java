package com.leo.aiteacher.service;

import com.leo.aiteacher.pojo.dto.CourseDto;

import java.util.List;

public interface CourseService {
    List<CourseDto> getCoursesByTeacherId(Integer teacherId);

    boolean removeCourse(String courseCode, Integer teacherId);

    boolean addCourse(CourseDto courseDto, Integer teacherId);
}