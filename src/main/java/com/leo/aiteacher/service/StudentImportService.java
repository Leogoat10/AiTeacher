package com.leo.aiteacher.service;

import com.leo.aiteacher.pojo.dto.ImportResult;
import org.springframework.web.multipart.MultipartFile;

public interface StudentImportService {
    ImportResult importStudents(String courseCode, Integer teacherId, MultipartFile file);
}