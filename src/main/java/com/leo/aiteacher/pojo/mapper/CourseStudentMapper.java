package com.leo.aiteacher.pojo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseStudentMapper {
    int insertIgnore(@Param("courseCode") String courseCode, @Param("studentId") Integer studentId);
}