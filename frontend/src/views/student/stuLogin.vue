<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import axios from 'axios'

// axios 实例，启用 withCredentials 以携带 session cookie
const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

const router = useRouter()

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const isLogin = ref(true)

const toggleForm = () => {
  isLogin.value = !isLogin.value
  username.value = ''
  password.value = ''
  confirmPassword.value = ''
}

const handleLogin = async () => {
  try {
    const response = await apiClient.post('/login/studentLogin', {
      userId: username.value,
      userName: username.value,
      passWord: password.value
    });

    if (response.status === 200) {
      const name = (response.data && (response.data.userName || response.data.name)) || username.value;
      sessionStorage.setItem('currentUser', name);
      sessionStorage.setItem('userRole', 'student');
      window.dispatchEvent(new CustomEvent('user-changed'));
      ElMessage.success('登录成功')
      router.push('/StudentInfo')
    } else {
      ElMessage.error('登录失败，请稍后重试')
    }
  } catch (error: any) {
    if (error.response && error.response.status === 401) {
      ElMessage.error('账号或密码错误')
    } else {
      ElMessage.error('登录失败，请稍后重试')
      console.error('登录请求出错:', error)
    }
  }
}

const handleRegister = async () => {
  if (password.value !== confirmPassword.value) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  if (password.value.length < 6) {
    ElMessage.error('密码长度不能少于6位')
    return
  }

  try {
    const response = await apiClient.post('/login/studentRegister', {
      userName: username.value,
      passWord: password.value
    });

    if (response.data === true) {
      ElMessage.success('学生账号注册成功！请使用新账号登录')
      toggleForm()
    } else {
      ElMessage.error('注册失败，用户名可能已存在')
    }
  } catch (error: any) {
    ElMessage.error('注册失败，请稍后重试')
    console.error('注册请求出错:', error)
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-box">
      <h2>
        <span class="icon-robot"></span>
        AI智能教师系统
      </h2>
      <div class="role-title">
        <h3>
          <span :class="isLogin ? 'icon-login' : 'icon-register'"></span>
          {{ isLogin ? '学生登录' : '学生注册' }}
        </h3>
      </div>

      <form v-if="isLogin" @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <span class="input-icon icon-user-field"></span>
          <input
              v-model="username"
              type="text"
              placeholder="请输入学号"
              required
          />
        </div>

        <div class="form-group">
          <span class="input-icon icon-lock-field"></span>
          <input v-model="password" type="password" placeholder="请输入密码" required />
        </div>

        <button type="submit" class="login-button">
          <span class="btn-icon-login"></span>
          登录
        </button>

        <div class="form-footer">
          <span>还没有账号？</span>
          <a href="#" @click.prevent="toggleForm">立即注册</a>
        </div>
      </form>

      <form v-else @submit.prevent="handleRegister" class="login-form">
        <div class="form-group">
          <span class="input-icon icon-user-field"></span>
          <input v-model="username" type="text" placeholder="请输入用户名" required />
        </div>

        <div class="form-group">
          <span class="input-icon icon-lock-field"></span>
          <input v-model="password" type="password" placeholder="请输入密码（至少6位）" required />
        </div>

        <div class="form-group">
          <span class="input-icon icon-check-field"></span>
          <input v-model="confirmPassword" type="password" placeholder="请确认密码" required />
        </div>

        <button type="submit" class="login-button">
          <span class="btn-icon-register"></span>
          注册
        </button>

        <div class="form-footer">
          <span>已有账号？</span>
          <a href="#" @click.prevent="toggleForm">立即登录</a>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
/* 修复图标斜体问题 - 重置所有字体样式 */
.login-container * {
  font-style: normal !important;
}

.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  /* 校园学生风格背景图 - 青春校园 + 学生元素 */
  background: linear-gradient(135deg, rgba(0, 0, 0, 0.5) 0%, rgba(0, 0, 0, 0.3) 100%),
  url('https://images.pexels.com/photos/256490/pexels-photo-256490.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
}

.login-box {
  width: 100%;
  max-width: 420px;
  padding: 2rem;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(2px);
  border-radius: 24px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
  text-align: center;
  transition: transform 0.2s;
}

.login-box:hover {
  transform: translateY(-3px);
}

h2 {
  margin-bottom: 1rem;
  color: #1e2f4e;
  font-size: 1.8rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-style: normal;
}

/* 图标样式 - 使用背景图片 + SVG 方式，完全避免斜体问题 */
.icon-robot {
  display: inline-block;
  width: 32px;
  height: 32px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23409eff'%3E%3Cpath d='M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-1v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-1H2a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1h1a7 7 0 0 1 7-7h1V5.73c-.6-.34-1-.99-1-1.73a2 2 0 0 1 2-2zM7.5 13A1.5 1.5 0 1 0 7.5 16 1.5 1.5 0 0 0 7.5 13zm9 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM12 14a1 1 0 0 0-1 1v2a1 1 0 0 0 2 0v-2a1 1 0 0 0-1-1z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
  font-style: normal;
}

.icon-login {
  display: inline-block;
  width: 20px;
  height: 20px;
  margin-right: 6px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23409eff'%3E%3Cpath d='M11 7L9.6 8.4l2.6 2.6H2v2h10.2l-2.6 2.6L11 17l5-5-5-5zm9-5h-8v2h8v14h-8v2h8c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
  vertical-align: middle;
}

.icon-register {
  display: inline-block;
  width: 20px;
  height: 20px;
  margin-right: 6px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23409eff'%3E%3Cpath d='M15 14c-2.67 0-8 1.33-8 4v2h16v-2c0-2.67-5.33-4-8-4zm-9-4V7H4v3H1v2h3v3h2v-3h3v-2H6zm9-2c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
  vertical-align: middle;
}

.input-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
  pointer-events: none;
  font-style: normal;
}

.icon-user-field {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%238c9bb0'%3E%3Cpath d='M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/%3E%3C/svg%3E");
}

.icon-lock-field {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%238c9bb0'%3E%3Cpath d='M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z'/%3E%3C/svg%3E");
}

.icon-check-field {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%238c9bb0'%3E%3Cpath d='M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z'/%3E%3C/svg%3E");
}

.btn-icon-login {
  display: inline-block;
  width: 18px;
  height: 18px;
  margin-right: 6px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='white'%3E%3Cpath d='M11 7L9.6 8.4l2.6 2.6H2v2h10.2l-2.6 2.6L11 17l5-5-5-5zm9-5h-8v2h8v14h-8v2h8c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
  vertical-align: middle;
}

.btn-icon-register {
  display: inline-block;
  width: 18px;
  height: 18px;
  margin-right: 6px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='white'%3E%3Cpath d='M15 14c-2.67 0-8 1.33-8 4v2h16v-2c0-2.67-5.33-4-8-4zm-9-4V7H4v3H1v2h3v3h2v-3h3v-2H6zm9-2c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
  vertical-align: middle;
}

.role-title h3 {
  margin-bottom: 1.5rem;
  color: #409eff;
  font-size: 1.3rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-style: normal;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
}

.form-group {
  position: relative;
}

.form-group input {
  width: 100%;
  padding: 0.9rem 1rem 0.9rem 2.5rem;
  border: 2px solid #e1e5e9;
  border-radius: 12px;
  font-size: 1rem;
  box-sizing: border-box;
  transition: all 0.3s;
  background: white;
  font-style: normal;
}

.form-group input:focus {
  outline: none;
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
}

.login-button {
  padding: 0.9rem;
  background: linear-gradient(135deg, #409eff, #2c6edf);
  color: white;
  border: none;
  border-radius: 40px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;
  font-style: normal;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.4);
}

.form-footer {
  text-align: center;
  font-size: 0.9rem;
  color: #666;
  font-style: normal;
}

.form-footer a {
  color: #409eff;
  text-decoration: none;
  margin-left: 0.5rem;
}

.form-footer a:hover {
  text-decoration: underline;
}
</style>