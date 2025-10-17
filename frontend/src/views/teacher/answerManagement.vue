<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

interface Course {
  courseCode: string;
  courseName: string;
}

interface StudentInfo {
  studentId: number;
  studentName: string;
  studentNumber: string;
  answerCount: number;
}

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

const router = useRouter()
const loading = ref(false)
const error = ref<string | null>(null)
const courses = ref<Course[]>([])
const selectedCourse = ref<string>('')
const students = ref<StudentInfo[]>([])
const filterStudentName = ref('')

// 获取教师的课程列表
async function fetchCourses() {
  loading.value = true
  error.value = null
  try {
    const res = await apiClient.get('/course/list')
    courses.value = res.data as Course[]
    if (courses.value.length > 0) {
      selectedCourse.value = courses.value[0].courseCode
      await fetchCourseStudents()
    }
  } catch (err: any) {
    if (err.response?.status === 401) {
      error.value = '未登录或会话失效，请重新登录'
      setTimeout(() => router.push('/teacherLogin'), 2000)
    } else {
      error.value = '获取课程列表失败: ' + (err.response?.data || err.message)
    }
  } finally {
    loading.value = false
  }
}

// 获取课程下的学生列表
async function fetchCourseStudents() {
  if (!selectedCourse.value) return
  
  loading.value = true
  error.value = null
  try {
    const res = await apiClient.get(`/studentAnswer/course/${selectedCourse.value}/students`)
    students.value = res.data as StudentInfo[]
  } catch (err: any) {
    if (err.response?.status === 401) {
      error.value = '未登录或会话失效，请重新登录'
      setTimeout(() => router.push('/teacherLogin'), 2000)
    } else {
      error.value = '获取学生列表失败: ' + (err.response?.data || err.message)
    }
  } finally {
    loading.value = false
  }
}

// 切换课程
async function onCourseChange() {
  await fetchCourseStudents()
}

// 过滤后的学生列表
const filteredStudents = computed(() => {
  let results = students.value
  
  if (filterStudentName.value) {
    results = results.filter(s => 
      s.studentName.toLowerCase().includes(filterStudentName.value.toLowerCase()) ||
      s.studentNumber.toLowerCase().includes(filterStudentName.value.toLowerCase())
    )
  }
  
  return results
})

// 查看学生答题历史
function viewStudentAnswers(student: StudentInfo) {
  router.push({
    name: 'studentAnswerHistory',
    params: {
      studentId: student.studentId,
      courseCode: selectedCourse.value
    },
    query: {
      studentName: student.studentName
    }
  })
}

// 返回首页
function goBack() {
  router.push('/teacherInfo')
}

onMounted(() => {
  fetchCourses()
})
</script>

<template>
  <div class="answer-management-container">
    <div class="header">
      <h1>学生答题管理</h1>
      <button @click="goBack" class="back-btn">返回首页</button>
    </div>

    <div v-if="error" class="error-message">{{ error }}</div>
    <div v-if="loading" class="loading">加载中...</div>

    <div v-if="!loading && !error" class="content">
      <!-- 课程选择 -->
      <div class="course-selector">
        <label>选择课程：</label>
        <select v-model="selectedCourse" @change="onCourseChange">
          <option v-for="course in courses" :key="course.courseCode" :value="course.courseCode">
            {{ course.courseName }} ({{ course.courseCode }})
          </option>
        </select>
      </div>

      <!-- 过滤器 -->
      <div class="filters">
        <div class="filter-item">
          <label>学生搜索：</label>
          <input v-model="filterStudentName" type="text" placeholder="搜索学生姓名或学号">
        </div>
      </div>

      <!-- 学生列表表格 -->
      <div class="table-container">
        <table v-if="filteredStudents.length > 0">
          <thead>
            <tr>
              <th>学生姓名</th>
              <th>学号</th>
              <th>答题数量</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="student in filteredStudents" :key="student.studentId">
              <td>{{ student.studentName }}</td>
              <td>{{ student.studentNumber }}</td>
              <td>{{ student.answerCount }}</td>
              <td>
                <button @click="viewStudentAnswers(student)" class="btn-view">查看答题历史</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="no-data">
          该课程下暂无学生答题
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.answer-management-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  font-family: Arial, sans-serif;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.header h1 {
  font-size: 28px;
  color: #333;
}

.back-btn {
  padding: 10px 20px;
  background-color: #6c757d;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.back-btn:hover {
  background-color: #5a6268;
}

.error-message {
  background-color: #f8d7da;
  color: #721c24;
  padding: 15px;
  border-radius: 5px;
  margin-bottom: 20px;
}

.loading {
  text-align: center;
  padding: 40px;
  font-size: 18px;
  color: #666;
}

.course-selector {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.course-selector label {
  font-weight: bold;
  font-size: 16px;
}

.course-selector select {
  padding: 8px 15px;
  font-size: 14px;
  border: 1px solid #ddd;
  border-radius: 5px;
  min-width: 300px;
}

.filters {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 5px;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-item label {
  font-weight: bold;
}

.filter-item input {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  min-width: 200px;
}

.table-container {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
}

thead {
  background-color: #007bff;
  color: white;
}

th {
  padding: 15px;
  text-align: left;
  font-weight: bold;
}

tbody tr {
  border-bottom: 1px solid #dee2e6;
}

tbody tr:hover {
  background-color: #f8f9fa;
}

td {
  padding: 12px 15px;
}

.btn-view {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  background-color: #007bff;
  color: white;
}

.btn-view:hover {
  background-color: #0056b3;
}

.no-data {
  padding: 40px;
  text-align: center;
  color: #666;
  font-size: 16px;
}
</style>
