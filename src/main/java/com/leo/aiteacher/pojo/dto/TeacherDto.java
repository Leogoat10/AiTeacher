package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("teachers")
public class TeacherDto {
    private String teacherName;
    private Integer teacherId;
    private String password;

}
