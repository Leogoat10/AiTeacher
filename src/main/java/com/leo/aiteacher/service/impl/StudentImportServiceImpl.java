package com.leo.aiteacher.service.impl;

import com.leo.aiteacher.pojo.dto.StuDto;
import com.leo.aiteacher.pojo.mapper.CourseMapper;
import com.leo.aiteacher.pojo.mapper.CourseStudentMapper;
import com.leo.aiteacher.pojo.mapper.StudentMapper;
import com.leo.aiteacher.pojo.dto.ImportResult;
import com.leo.aiteacher.service.StudentImportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class StudentImportServiceImpl implements StudentImportService {

    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;

    public StudentImportServiceImpl(StudentMapper studentMapper,
                                    CourseMapper courseMapper,
                                    CourseStudentMapper courseStudentMapper) {
        this.studentMapper = studentMapper;
        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;
    }

    @Override
    @Transactional
    public ImportResult importStudents(String courseCode, Integer teacherId, MultipartFile file) {
        ImportResult result = new ImportResult();

        if (file == null || file.isEmpty()) {
            result.setMessage("文件为空");
            return result;
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            result.setMessage("仅支持 .xlsx 或 .xls");
            return result;
        }

        Integer owned = courseMapper.countByCourseCodeAndTeacherId(courseCode, teacherId);
        if (owned == null || owned == 0) {
            result.setMessage("课程不存在或无权限");
            return result;
        }

        int total = 0, inserted = 0, enrolled = 0, skipped = 0, failed = 0;

        try (InputStream is = file.getInputStream();
             Workbook wb = name.endsWith(".xlsx") ? new XSSFWorkbook(is) : new HSSFWorkbook(is)) {

            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) {
                result.setMessage("Excel 无工作表");
                return result;
            }

            // 读取表头，定位 `student_name` 和 `student_id` 列索引
            Row header = sheet.getRow(0);
            if (header == null) {
                result.setMessage("Excel 缺少表头");
                return result;
            }
            Map<String, Integer> idx = new HashMap<>();
            for (Cell cell : header) {
                cell.setCellType(CellType.STRING);
                String key = cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim().toLowerCase(Locale.ROOT);
                idx.put(key, cell.getColumnIndex());
            }
            Integer nameCol = idx.get("student_name");
            Integer idCol = idx.get("student_id");
            if (nameCol == null || idCol == null) {
                result.setMessage("表头需包含 student_name 与 student_id");
                return result;
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                total++;

                String studentName = readAsString(row.getCell(nameCol));
                Integer studentId = readAsInteger(row.getCell(idCol));

                if (studentId == null || studentName == null || studentName.isEmpty()) {
                    skipped++;
                    continue;
                }

                try {
                    StuDto exist = studentMapper.getStudentById(studentId);
                    if (exist == null) {
                        StuDto s = new StuDto();
                        s.setStudentId(studentId);
                        s.setStudentName(studentName);
                        s.setPassword("123456");
                        studentMapper.insertStudent(s);
                        inserted++;
                    }
                    int affected = courseStudentMapper.insertIgnore(courseCode, studentId);
                    if (affected > 0) {
                        enrolled++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    log.warn("导入第{}行失败: {}", r + 1, e.getMessage());
                    failed++;
                }
            }

        } catch (Exception ex) {
            log.error("导入失败: {}", ex.getMessage(), ex);
            result.setMessage("导入失败: " + ex.getMessage());
            return result;
        }

        result.setTotalRows(total);
        result.setInsertedStudents(inserted);
        result.setEnrolled(enrolled);
        result.setSkipped(skipped);
        result.setFailed(failed);
        result.setMessage("导入完成");
        return result;
    }

    private static String readAsString(Cell cell) {
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        String s = cell.getStringCellValue();
        return s == null ? null : s.trim();
    }

    private static Integer readAsInteger(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            }
            cell.setCellType(CellType.STRING);
            String s = cell.getStringCellValue();
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s.replaceAll("\\.0+$", ""));
        } catch (Exception e) {
            return null;
        }
    }
}