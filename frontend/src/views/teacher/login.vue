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
    const response = await apiClient.post('/login/teacherLogin', {
      userId: username.value,
      userName: username.value,
      passWord: password.value
    });

    if (response.status === 200) {
      // 尝试从后端返回中取姓名，回退到输入的用户名
      const name = (response.data && (response.data.userName || response.data.name)) || username.value;
      sessionStorage.setItem('currentUser', name);
      // 通知同页组件更新（storage 事件只在不同窗口触发）
      window.dispatchEvent(new CustomEvent('user-changed'));
      ElMessage.success('登录成功')
      router.push('/TeacherInfo')
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
    const response = await apiClient.post('/login/teacherRegister', {
      userName: username.value,
      passWord: password.value
    });

    if (response.data === true) {
      ElMessage.success('教师账号注册成功！请使用新账号登录')
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
      <h2>AI智能教师系统</h2>
      <div class="role-title">
        <h3>{{ isLogin ? '教师登录' : '教师注册' }}</h3>
      </div>

      <form v-if="isLogin" @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <input
            v-model="username"
            type="text"
            placeholder="请输入工号或姓名"
            required
          />
        </div>

        <div class="form-group">
          <input v-model="password" type="password" placeholder="请输入密码" required />
        </div>

        <button type="submit" class="login-button">登录</button>

        <div class="form-footer">
          <span>还没有账号？</span>
          <a href="#" @click.prevent="toggleForm">立即注册</a>
        </div>
      </form>

      <form v-else @submit.prevent="handleRegister" class="login-form">
        <div class="form-group">
          <input v-model="username" type="text" placeholder="请输入用户名" required />
        </div>

        <div class="form-group">
          <input v-model="password" type="password" placeholder="请输入密码（至少6位）" required />
        </div>

        <div class="form-group">
          <input v-model="confirmPassword" type="password" placeholder="请确认密码" required />
        </div>

        <button type="submit" class="login-button">注册</button>

        <div class="form-footer">
          <span>已有账号？</span>
          <a href="#" @click.prevent="toggleForm">立即登录</a>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-box {
  width: 100%;
  max-width: 400px;
  padding: 2rem;
  background: white;
  border-radius: 10px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
  text-align: center;
}
h2 { margin-bottom: 1.5rem; color: #333; font-size: 1.8rem; }
.role-title h3 { margin-bottom: 1.5rem; color: #409eff; font-size: 1.3rem; }
.login-form { display: flex; flex-direction: column; gap: 1.2rem; }
.form-group input {
  width: 100%; padding: 1rem; border: 2px solid #e1e5e9; border-radius: 6px; font-size: 1rem;
  box-sizing: border-box; transition: border-color 0.3s;
}
.form-group input:focus { outline: none; border-color: #409eff; box-shadow: 0 0 0 3px rgba(64,158,255,0.1); }

.login-button {
  padding: 1rem; background: linear-gradient(135deg, #409eff, #66b1ff); color: white; border: none; border-radius: 6px;
  font-size: 1.1rem; cursor: pointer; transition: all 0.3s; font-weight: 500;
}
.login-button:hover { transform: translateY(-2px); box-shadow: 0 5px 15px rgba(64, 158, 255, 0.3); }
.form-footer { text-align: center; font-size: 0.9rem; color: #666; }
.form-footer a { color: #409eff; text-decoration: none; margin-left: 0.5rem; }
</style>
