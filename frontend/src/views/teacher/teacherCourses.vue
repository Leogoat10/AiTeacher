<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

interface Course {
  id: number;
  courseName: string;
  courseCode: string;
  teacherId: number;
}

// 新增：学生信息类型
interface StudentInfo {
  studentId: number;
  studentName: string;
}

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

const courses = ref<Course[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const showAddForm = ref(false)
const newCourseName = ref('')
const newCourseCode = ref('')
const addLoading = ref(false)
const addError = ref<string | null>(null)

const deletingCourseCode = ref<string | null>(null)
const deleteError = ref<string | null>(null)
const showDeleteConfirm = ref(false)
const courseToDelete = ref<Course | null>(null)

// 导入学生相关
const showImportDialog = ref(false)
const courseToImport = ref<Course | null>(null)
const selectedFile = ref<File | null>(null)
const importLoading = ref(false)
const importError = ref<string | null>(null)
const importResult = ref<string | null>(null)

// 新增：课程学生列表弹窗状态
const showStudentsDialog = ref(false)
const studentsLoading = ref(false)
const studentsError = ref<string | null>(null)
const students = ref<StudentInfo[]>([])
const courseForStudents = ref<Course | null>(null)


async function openStudents(c: Course) {
  courseForStudents.value = c
  students.value = []
  studentsError.value = null
  showStudentsDialog.value = true
  studentsLoading.value = true
  try {
    const res = await apiClient.get('/course/students', {
      params: { courseCode: c.courseCode }
    })
    students.value = res.data as StudentInfo[]
  } catch (err: any) {
    if (err.response && err.response.data) {
      studentsError.value = typeof err.response.data === 'string' ? err.response.data : '查询失败'
    } else if (err.response) {
      studentsError.value = '服务器错误: ' + err.response.status
    } else {
      studentsError.value = '网络或服务器无法访问'
    }
  } finally {
    studentsLoading.value = false
  }
}

function closeStudents() {
  showStudentsDialog.value = false
  courseForStudents.value = null
  students.value = []
  studentsError.value = null
}

async function loadCourses() {
  loading.value = true
  error.value = null
  courses.value = []
  try {
    const res = await apiClient.get('/course/teacherCourse')
    courses.value = res.data as Course[]
  } catch (err: any) {
    if (err.response) {
      error.value = '服务器错误: ' + err.response.status
    } else {
      error.value = '网络或服务器无法访问'
    }
  } finally {
    loading.value = false
  }
}

async function submitAddCourse() {
  addError.value = null
  if (!newCourseName.value || !newCourseCode.value) {
    addError.value = '请填写课程名称和课程代码'
    return
  }
  addLoading.value = true
  try {
    const payload = {
      courseName: newCourseName.value,
      courseCode: newCourseCode.value
    }
    await apiClient.post('/course/addCourse', payload)
    await loadCourses()
    newCourseName.value = ''
    newCourseCode.value = ''
    showAddForm.value = false
  } catch (err: any) {
    if (err.response && err.response.data) {
      addError.value = String(err.response.data)
    } else if (err.response) {
      addError.value = '服务器错误: ' + err.response.status
    } else {
      addError.value = '网络或服务器无法访问'
    }
  } finally {
    addLoading.value = false
  }
}

async function deleteCourse(course: Course) {
  courseToDelete.value = course
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (!courseToDelete.value) return

  deleteError.value = null
  deletingCourseCode.value = courseToDelete.value.courseCode

  try {
    await apiClient.post('/course/removeCourse', {
      courseCode: courseToDelete.value.courseCode
    })
    await loadCourses()
    showDeleteConfirm.value = false
  } catch (err: any) {
    if (err.response && err.response.data) {
      deleteError.value = String(err.response.data)
    } else if (err.response) {
      deleteError.value = '服务器错误: ' + err.response.status
    } else {
      deleteError.value = '网络或服务器无法访问'
    }
  } finally {
    deletingCourseCode.value = null
  }
}

function cancelDelete() {
  showDeleteConfirm.value = false
  courseToDelete.value = null
}

function handleOverlayClick(event: Event) {
  if (event.target === event.currentTarget) {
    showAddForm.value = false
  }
}

function handleDeleteOverlayClick(event: Event) {
  if (event.target === event.currentTarget) {
    showDeleteConfirm.value = false
    courseToDelete.value = null
  }
}

function openImport(c: Course) {
  courseToImport.value = c
  selectedFile.value = null
  importError.value = null
  importResult.value = null
  showImportDialog.value = true
}

function onFileChange(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files || files.length === 0) {
    selectedFile.value = null
    return
  }
  const file = files[0]
  const name = file.name.toLowerCase()
  if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
    importError.value = '只支持 .xlsx 或 .xls'
    selectedFile.value = null
    return
  }
  importError.value = null
  selectedFile.value = file
}

async function submitImport() {
  if (!courseToImport.value) return
  if (!selectedFile.value) {
    importError.value = '请先选择 Excel 文件'
    return
  }
  importError.value = null
  importLoading.value = true
  importResult.value = null
  try {
    const fd = new FormData()
    fd.append('courseCode', courseToImport.value.courseCode)
    fd.append('file', selectedFile.value)
    const res = await apiClient.post('/course/importStudents', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const data = res.data
    importResult.value = `总行: ${data.totalRows}，新增学生: ${data.insertedStudents}，加入课程: ${data.enrolled}，跳过: ${data.skipped}，失败: ${data.failed}`
    await loadCourses()
  } catch (err: any) {
    if (err.response && err.response.data) {
      importError.value = typeof err.response.data === 'string' ? err.response.data : (err.response.data.message || '导入失败')
    } else if (err.response) {
      importError.value = '服务器错误: ' + err.response.status
    } else {
      importError.value = '网络或服务器无法访问'
    }
  } finally {
    importLoading.value = false
  }
}

function closeImport() {
  showImportDialog.value = false
  courseToImport.value = null
  selectedFile.value = null
}
onMounted(() => {
  loadCourses()
})
</script>

<template>
  <div class="teacher-courses">
    <div class="header">
      <h1>教师课程管理</h1>
      <div class="header-actions">
        <button class="btn-primary" @click="showAddForm = !showAddForm">
          <i class="icon-plus"></i>
          添加课程
        </button>
      </div>
    </div>

    <!-- 导入学生对话框 -->
    <div v-if="showImportDialog" class="add-form-overlay" @click.self="closeImport">
      <div class="add-form-modal" @click.stop>
        <div class="modal-header">
          <h2>批量导入学生</h2>
          <button class="close-btn" @click="closeImport">×</button>
        </div>
        <div class="modal-body">
          <p>课程：<strong>{{ courseToImport?.courseName }}</strong>（{{ courseToImport?.courseCode }}）</p>
          <div class="form-group">
            <label>选择 Excel 文件（.xlsx 或 .xls），列名需包含 student_name、student_id</label>
            <input type="file" accept=".xlsx,.xls" class="form-input" @change="onFileChange" />
          </div>
          <div v-if="importError" class="error-message">
            <i class="icon-error"></i>
            {{ importError }}
          </div>
          <div v-if="importResult" style="margin-top:8px;color:#2f855a;">
            {{ importResult }}
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeImport">取消</button>
          <button class="btn-primary" :disabled="importLoading" @click="submitImport">
            {{ importLoading ? '导入中...' : '开始导入' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 新增：课程学生列表对话框 -->
    <div v-if="showStudentsDialog" class="add-form-overlay" @click.self="closeStudents">
      <div class="add-form-modal" @click.stop>
        <div class="modal-header">
          <h2>课程学生列表</h2>
          <button class="close-btn" @click="closeStudents">×</button>
        </div>
        <div class="modal-body">
          <p>课程：<strong>{{ courseForStudents?.courseName }}</strong>（{{ courseForStudents?.courseCode }}）</p>
          <div v-if="studentsLoading" class="loading-state">
            <div class="spinner"></div>
            <p>正在加载学生...</p>
          </div>
          <div v-else>
            <div v-if="studentsError" class="error-message">
              <i class="icon-error"></i>{{ studentsError }}
            </div>
            <div v-else>
              <table class="courses-table">
                <thead>
                <tr>
                  <th>学号</th>
                  <th>姓名</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="s in students" :key="s.studentId">
                  <td>{{ s.studentId }}</td>
                  <td>{{ s.studentName }}</td>
                </tr>
                <tr v-if="students.length === 0">
                  <td colspan="3" style="text-align:center;color:#718096;">暂无学生</td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeStudents">关闭</button>
        </div>
      </div>
    </div>

    <!-- 添加课程模态框 -->
    <div v-if="showAddForm" class="add-form-overlay" @click="handleOverlayClick">
      <div class="add-form-modal" @click.stop>
        <div class="modal-header">
          <h2>添加新课程</h2>
          <button class="close-btn" @click="showAddForm = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label for="courseName">课程名称</label>
            <input
              id="courseName"
              v-model="newCourseName"
              placeholder="请输入课程名称"
              class="form-input"
              :class="{ 'error': addError && !newCourseName }"
            />
          </div>
          <div class="form-group">
            <label for="courseCode">课程代码</label>
            <input
              id="courseCode"
              v-model="newCourseCode"
              placeholder="请输入课程代码"
              class="form-input"
              :class="{ 'error': addError && !newCourseCode }"
            />
          </div>
          <div v-if="addError" class="error-message">
            <i class="icon-error"></i>
            {{ addError }}
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="showAddForm = false">取消</button>
          <button
            class="btn-primary"
            @click="submitAddCourse"
            :disabled="addLoading"
          >
            {{ addLoading ? '保存中...' : '保存课程' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 删除确认对话框 -->
    <div v-if="showDeleteConfirm" class="delete-confirm-overlay" @click="handleDeleteOverlayClick">
      <div class="delete-confirm-modal" @click.stop>
        <div class="modal-icon warning">
          <i class="icon-warning"></i>
        </div>
        <h3>确认删除课程</h3>
        <p>您确定要删除课程 <span class="highlight">{{ courseToDelete?.courseName }}</span> 吗？此操作无法撤销。</p>
        <div class="modal-footer">
          <button class="btn-secondary" @click="cancelDelete">取消</button>
          <button
            class="btn-danger"
            @click="confirmDelete"
            :disabled="deletingCourseCode === courseToDelete?.courseCode"
          >
            {{ deletingCourseCode === courseToDelete?.courseCode ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>

    <div class="content-card">
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <p>正在加载课程信息...</p>
      </div>

      <div v-else>
        <div v-if="error" class="error-alert">
          <i class="icon-error"></i>
          {{ error }}
          <button @click="loadCourses" class="retry-btn">重试</button>
        </div>

        <div v-else-if="courses.length === 0" class="empty-state">
          <i class="icon-course"></i>
          <h3>暂无课程</h3>
          <p>您还没有创建任何课程</p>
          <button class="btn-primary" @click="showAddForm = true">创建第一个课程</button>
        </div>

        <div v-else class="courses-table-container">
          <table class="courses-table">
            <thead>
            <tr>
              <th>课程名称</th>
              <th>课程代码</th>
              <th>操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="c in courses" :key="c.courseCode" class="course-row">
              <td>
                <div class="course-name" style="cursor:pointer;text-decoration:underline;" @click="openStudents(c)">
                  {{ c.courseName }}
                </div>
              </td>
              <td><span class="course-code">{{ c.courseCode }}</span></td>
              <td>
                <div class="action-buttons">
                  <button class="btn-icon btn-edit" @click="openImport(c)">
                    <i class="icon-edit"></i>
                  </button>
                  <button class="btn-icon btn-delete" :disabled="deletingCourseCode === c.courseCode" @click="deleteCourse(c)">
                    <i class="icon-delete"></i>
                  </button>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
          <div v-if="deleteError" class="error-message" style="margin-top: 12px;">
            <i class="icon-error"></i>
            {{ deleteError }}
          </div>
        </div>
      </div>
    </div>
  </div>


</template>

<style scoped>
.teacher-courses {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px 0;
}

.header h1 {
  font-size: 28px;
  font-weight: 700;
  color: #2d3748;
  margin: 0;
}

.header-actions .btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 6px rgba(50, 50, 93, 0.11), 0 1px 3px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}

.header-actions .btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 7px 14px rgba(50, 50, 93, 0.1), 0 3px 6px rgba(0, 0, 0, 0.08);
}

.content-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  padding: 24px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid rgba(102, 126, 234, 0.2);
  border-top: 4px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error-alert {
  background-color: #fed7d7;
  color: #c53030;
  padding: 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.retry-btn {
  background-color: #e53e3e;
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  margin-left: auto;
}

.empty-state {
  text-align: center;
  padding: 48px 0;
  color: #718096;
}

.empty-state .icon-course {
  font-size: 48px;
  margin-bottom: 16px;
  color: #cbd5e0;
}

.empty-state h3 {
  margin: 0 0 8px;
  color: #4a5568;
}

.empty-state p {
  margin: 0 0 24px;
}

.empty-state .btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  margin: 0 auto;
}

.courses-table-container {
  overflow-x: auto;
}

.courses-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.courses-table thead {
  background-color: #f7fafc;
}

.courses-table th {
  padding: 16px;
  text-align: left;
  font-weight: 600;
  color: #4a5568;
  border-bottom: 2px solid #e2e8f0;
}

.courses-table td {
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
  color: #4a5568;
}

.course-row:hover {
  background-color: #f7fafc;
}

.course-name {
  font-weight: 600;
  color: #2d3748;
}

.course-code {
  background-color: #ebf8ff;
  color: #3182ce;
  padding: 4px 8px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.btn-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 16px;
}

.btn-edit {
  background-color: #ebf8ff;
  color: #3182ce;
}

.btn-edit:hover {
  background-color: #bee3f8;
  transform: translateY(-1px);
}

.btn-delete {
  background-color: #fff5f5;
  color: #e53e3e;
}

.btn-delete:hover {
  background-color: #fed7d7;
  transform: translateY(-1px);
}

.btn-delete:disabled {
  opacity: 0.6;
  transform: none;
  cursor: not-allowed;
}

.icon-plus::before,
.icon-edit::before,
.icon-delete::before,
.icon-error::before,
.icon-course::before,
.icon-warning::before {
  content: "★"; /* 实际项目中应使用真正的图标字体或SVG */
}

/* 添加课程表单样式 */
.add-form-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
}

.add-form-modal {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  width: 100%;
  max-width: 500px;
  overflow: hidden;
  animation: modalEnter 0.3s ease-out;
}

@keyframes modalEnter {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #2d3748;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #a0aec0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background-color: #f7fafc;
  color: #718096;
}

.modal-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: #4a5568;
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 16px;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-input.error {
  border-color: #e53e3e;
  box-shadow: 0 0 0 3px rgba(229, 62, 62, 0.1);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
  background-color: #f7fafc;
}

.btn-secondary {
  background-color: #edf2f7;
  color: #4a5568;
  border: none;
  padding: 10px 20px;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-secondary:hover {
  background-color: #e2e8f0;
}

.btn-danger {
  background-color: #e53e3e;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-danger:hover:not(:disabled) {
  background-color: #c53030;
}

.btn-danger:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.error-message {
  color: #e53e3e;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

/* 删除确认对话框样式 */
.delete-confirm-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
}

.delete-confirm-modal {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  width: 100%;
  max-width: 450px;
  padding: 32px;
  text-align: center;
  animation: modalEnter 0.3s ease-out;
}

.modal-icon.warning {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background-color: #fff5f5;
  color: #e53e3e;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  font-size: 24px;
}

.delete-confirm-modal h3 {
  margin: 0 0 12px;
  font-size: 20px;
  font-weight: 600;
  color: #2d3748;
}

.delete-confirm-modal p {
  margin: 0 0 24px;
  color: #4a5568;
  line-height: 1.6;
}

.highlight {
  font-weight: 600;
  color: #e53e3e;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 6px rgba(50, 50, 93, 0.11), 0 1px 3px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 7px 14px rgba(50, 50, 93, 0.1), 0 3px 6px rgba(0, 0, 0, 0.08);
}

.btn-primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
  box-shadow: 0 4px 6px rgba(50, 50, 93, 0.11), 0 1px 3px rgba(0, 0, 0, 0.08);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .teacher-courses {
    padding: 16px;
  }

  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .content-card {
    padding: 16px;
  }

  .courses-table th,
  .courses-table td {
    padding: 12px 8px;
  }

  .add-form-modal,
  .delete-confirm-modal {
    margin: 16px;
    max-width: calc(100% - 32px);
  }

  .modal-body {
    padding: 20px;
  }

  .modal-header,
  .modal-footer {
    padding: 16px 20px;
  }

  .delete-confirm-modal {
    padding: 24px;
  }
}
</style>
