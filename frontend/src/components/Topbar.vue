<template>
  <header v-if="visible" class="topbar" @click.stop>
    <div class="left" @click="goHome">ai智能教师</div>
    <div class="right">
      <button class="menu-btn" @click.stop="toggle">
        {{ currentUser }}
        <span class="caret" :class="{ open }">▾</span>
      </button>
      <div v-if="open" class="dropdown">
        <button class="dropdown-item" @click="goToInfo">用户信息</button>
        <button class="dropdown-item danger" @click="logout">登出</button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from "element-plus"

const route = useRoute()
const router = useRouter()
const open = ref(false)
const currentUser = ref('用户') // 默认显示

const visible = computed(() => {
  const p = route.path || ''
  return !(p === '/' || /login/i.test(p) || /welcome/i.test(p));
})

function toggle() {
  open.value = !open.value
}

function goHome() {
  router.push('/')
  open.value = false
}

function goToInfo() {
  router.push('/teacherInfo')
  open.value = false
}

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

const logout = async () => {
  try {
    const res = await apiClient.post('/login/logout')
    if (res.status === 200) {
      // 清除当前用户并通知更新
      sessionStorage.removeItem('currentUser')
      window.dispatchEvent(new CustomEvent('user-changed'))
      ElMessage.success('已成功登出')
      router.push('/')
    } else {
      ElMessage.error('登出失败，请稍后重试')
    }
  } catch (e) {
    console.error('登出出错:', e)
    ElMessage.error('登出失败，请检查网络或稍后重试')
  }
}

function updateUserFromStorage() {
  currentUser.value = sessionStorage.getItem('currentUser') || '用户'
}

function onClickOutside(e: Event) {
  const el = (e.target as HTMLElement)
  if (!el.closest('.topbar')) {
    open.value = false
  }
}

onMounted(() => {
  window.addEventListener('click', onClickOutside)
  // 跨窗口 storage 事件
  window.addEventListener('storage', updateUserFromStorage)
  // 同窗口自定义事件，用于登录后立即更新
  window.addEventListener('user-changed', updateUserFromStorage)
  updateUserFromStorage()
})
onBeforeUnmount(() => {
  window.removeEventListener('click', onClickOutside)
  window.removeEventListener('storage', updateUserFromStorage)
  window.removeEventListener('user-changed', updateUserFromStorage)
})
</script>

<style scoped>
  .topbar {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 1rem;
    background: linear-gradient(90deg,#2b6cb0,#4a90e2);
    color: #fff;
    box-shadow: 0 1px 6px rgba(0,0,0,0.08);
    z-index: 1000; /* 提高优先级，覆盖侧边栏 */
  }
  .left {
    font-weight: 700;
    font-size: 1.05rem;
    cursor: pointer;
  }
  .right {
    position: relative;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
  .menu-btn {
    background: transparent;
    border: 0;
    color: #fff;
    padding: 0.4rem 0.6rem;
    border-radius: 6px;
    cursor: pointer;
    font-weight: 600;
  }
  .menu-btn:hover { background: rgba(255,255,255,0.06); }
  .caret { margin-left: 0.4rem; transition: transform 0.15s; display:inline-block; }
  .caret.open { transform: rotate(180deg); }

  .dropdown {
    position: absolute;
    right: 0;
    top: calc(100% + 8px);
    background: #fff;
    color: #222;
    border-radius: 8px;
    box-shadow: 0 6px 18px rgba(13,38,59,0.12);
    overflow: hidden;
    min-width: 150px;
  }
  .dropdown-item {
    display: block;
    width: 100%;
    padding: 0.65rem 0.9rem;
    text-align: left;
    background: transparent;
    border: none;
    cursor: pointer;
    font-weight: 600;
  }
  .dropdown-item:hover { background: #f3f6fb; }
  .dropdown-item.danger { color: #c53030; }
</style>