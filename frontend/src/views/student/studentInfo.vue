<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

interface StuDto {
  studentName: string;
  gender?: string;
  studentId: string;
  password?: string;
}

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

const loading = ref(false)
const error = ref<string | null>(null)
const student = ref<StuDto | null>(null)

async function fetchStudent() {
  error.value = null
  student.value = null
  loading.value = true
  try {
    const res = await apiClient.get('/Info/studentInfo')
    student.value = res.data as StuDto
  } catch (err: any) {
    if (err.response) {
      if (err.response.status === 401) {
        error.value = '未登录'
      } else {
        error.value = '服务器错误: ' + err.response.status
      }
    } else {
      error.value = '网络或服务器无法访问'
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchStudent()
})
</script>

<template>
  <div class="student-info-container">
    <div class="header">
      <h2>当前登录学生</h2>
      <button @click="fetchStudent" :disabled="loading" class="refresh-btn">
        <span v-if="loading">🔄 加载中...</span>
        <span v-else>↻ 刷新</span>
      </button>
    </div>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>

    <div v-if="student" class="student-card">
      <div class="avatar-placeholder">
        <span class="avatar-icon">🎓</span>
      </div>
      <div class="student-details">
        <h3>学生详情</h3>
        <div class="detail-item">
          <span class="label">学生ID:</span>
          <span class="value">{{ student.studentId }}</span>
        </div>
        <div class="detail-item">
          <span class="label">姓名:</span>
          <span class="value">{{ student.studentName }}</span>
        </div>
        <div v-if="student.gender" class="detail-item">
          <span class="label">性别:</span>
          <span class="value">{{ student.gender }}</span>
        </div>
        <div v-if="student.password" class="detail-item">
          <span class="label">密码:</span>
          <span class="value password-field">{{ student.password }}</span>
        </div>
      </div>
    </div>

    <div v-else-if="!loading && !error" class="empty-state">
      暂无学生信息
    </div>
  </div>
</template>

<style scoped>
.student-info-container {
  max-width: 600px;
  margin: 2rem auto;
  padding: 2rem;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #eee;
}

.header h2 {
  margin: 0;
  color: #333;
  font-weight: 600;
}

.refresh-btn {
  padding: 0.5rem 1rem;
  background-color: #409eff;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.3s ease;
}

.refresh-btn:hover:not(:disabled) {
  background-color: #337ecc;
  transform: translateY(-2px);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-message {
  padding: 1rem;
  background-color: #fef0f0;
  color: #f56565;
  border: 1px solid #fed7d7;
  border-radius: 6px;
  margin-bottom: 1rem;
}

.student-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #f8f9fa;
  border-radius: 10px;
  padding: 2rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.avatar-placeholder {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1.5rem;
}

.avatar-icon {
  font-size: 2rem;
}

.student-details {
  width: 100%;
}

.student-details h3 {
  text-align: center;
  margin-top: 0;
  margin-bottom: 1.5rem;
  color: #2d3748;
  font-weight: 600;
}

.detail-item {
  display: flex;
  margin-bottom: 1rem;
  padding: 0.8rem;
  background: white;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.label {
  font-weight: 600;
  color: #4a5568;
  min-width: 80px;
  margin-right: 1rem;
}

.value {
  color: #2d3748;
  flex: 1;
}

.password-field {
  font-family: monospace;
  letter-spacing: 2px;
}

.empty-state {
  text-align: center;
  padding: 2rem;
  color: #a0aec0;
  font-style: italic;
}

@media (max-width: 768px) {
  .student-info-container {
    margin: 1rem;
    padding: 1.5rem;
  }

  .header {
    flex-direction: column;
    gap: 1rem;
  }

  .detail-item {
    flex-direction: column;
    gap: 0.3rem;
  }

  .label {
    min-width: auto;
  }
}
</style>