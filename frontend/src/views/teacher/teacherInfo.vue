<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

interface TeacherDto {
  teacherName: string;
  gender?: string;
  teacherId: string;
  password?: string;
}

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

const loading = ref(false)
const error = ref<string | null>(null)
const teacher = ref<TeacherDto | null>(null)
const oldPassword = ref('')
const newPassword = ref('')
const passwordLoading = ref(false)
const passwordError = ref<string | null>(null)
const passwordSuccess = ref<string | null>(null)

async function fetchTeacher() {
  error.value = null
  teacher.value = null
  loading.value = true
  try {
    const res = await apiClient.get('/Info/teacherInfo')
    // 成功返回用户信息
    teacher.value = res.data as TeacherDto
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
  fetchTeacher()
})

async function changePassword() {
  passwordError.value = null
  passwordSuccess.value = null
  if (!oldPassword.value || !newPassword.value) {
    passwordError.value = '请输入原始密码和新密码'
    return
  }

  passwordLoading.value = true
  try {
    await apiClient.post('/Info/teacherInfo/password', {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value
    })
    passwordSuccess.value = '密码修改成功'
    oldPassword.value = ''
    newPassword.value = ''
    fetchTeacher()
  } catch (err: any) {
    passwordError.value = err.response?.data || '密码修改失败'
  } finally {
    passwordLoading.value = false
  }
}
</script>

<template>
  <div class="teacher-info-container">
    <div class="header">
      <h2>当前登录用户</h2>
      <button @click="fetchTeacher" :disabled="loading" class="refresh-btn">
        <span v-if="loading">🔄 加载中...</span>
        <span v-else>↻ 刷新</span>
      </button>
    </div>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>

    <div v-if="teacher" class="teacher-card">
      <div class="avatar-placeholder">
        <span class="avatar-icon">👨‍🏫</span>
      </div>
      <div class="teacher-details">
        <h3>教师详情</h3>
        <div class="detail-item">
          <span class="label">教师ID:</span>
          <span class="value">{{ teacher.teacherId }}</span>
        </div>
        <div class="detail-item">
          <span class="label">姓名:</span>
          <span class="value">{{ teacher.teacherName }}</span>
        </div>
        <div v-if="teacher.gender" class="detail-item">
          <span class="label">性别:</span>
          <span class="value">{{ teacher.gender }}</span>
        </div>
        <div v-if="teacher.password" class="detail-item">
          <span class="label">密码:</span>
          <span class="value password-field">{{ teacher.password }}</span>
        </div>
      </div>

      <details class="password-panel">
        <summary class="password-summary">修改密码</summary>
        <div class="password-form-row">
          <label for="teacher-old-password">原始密码</label>
          <input
            id="teacher-old-password"
            v-model="oldPassword"
            type="password"
            placeholder="请输入原始密码"
          />
        </div>
        <div class="password-form-row">
          <label for="teacher-new-password">新密码</label>
          <input
            id="teacher-new-password"
            v-model="newPassword"
            type="password"
            placeholder="请输入新密码"
          />
        </div>
        <div v-if="passwordError" class="password-message error">{{ passwordError }}</div>
        <div v-if="passwordSuccess" class="password-message success">{{ passwordSuccess }}</div>
        <button class="password-btn" @click="changePassword" :disabled="passwordLoading">
          {{ passwordLoading ? '提交中...' : '确认修改' }}
        </button>
      </details>
    </div>

    <div v-else-if="!loading && !error" class="empty-state">
      暂无教师信息
    </div>
  </div>
</template>


<style scoped>
.teacher-info-container {
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

.teacher-card {
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

.teacher-details {
  width: 100%;
}

.teacher-details h3 {
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

.password-panel {
  width: 100%;
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #e2e8f0;
}

.password-summary {
  font-weight: 600;
  color: #2d3748;
  cursor: pointer;
  margin-bottom: 0.8rem;
}

.password-panel[open] .password-summary {
  margin-bottom: 1rem;
}

.password-form-row {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin-bottom: 0.8rem;
}

.password-form-row label {
  font-size: 0.9rem;
  color: #4a5568;
}

.password-form-row input {
  padding: 0.6rem 0.7rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.95rem;
}

.password-btn {
  margin-top: 0.5rem;
  padding: 0.55rem 1rem;
  background-color: #409eff;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.password-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.password-message {
  margin-top: 0.3rem;
  font-size: 0.9rem;
}

.password-message.error {
  color: #d03050;
}

.password-message.success {
  color: #16a34a;
}

@media (max-width: 768px) {
  .teacher-info-container {
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
