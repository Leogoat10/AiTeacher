package com.leo.aiteacher.pojo.dto;

import lombok.Data;

@Data
public class ImportResult {
    private int totalRows;
    private int insertedStudents;
    private int enrolled;
    private int skipped;
    private int failed;
    private String message;
}