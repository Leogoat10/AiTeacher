package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("students")
public class StuDto {
    private String studentName;
    private Integer studentId;
    private String password;

}
