package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data

@TableName("courses")
public class CourseDto {
    private Integer id;
    private String courseName;
    private String courseCode;
    private Integer teacherId;
}