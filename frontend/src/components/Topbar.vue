<template>
  <header v-if="visible" class="topbar" @click.stop>
    <div class="left">
      <!-- 新增：汉堡菜单按钮 -->
      <button class="hamburger-btn" @click.stop="toggleSidebar" aria-label="Toggle sidebar">
        <span class="hamburger-icon">☰</span>
      </button>
      <div class="brand" @click="goHome">ai智能教师</div>
    </div>
    <div class="right">
      <button class="menu-btn" @click.stop="toggle">
        <span class="user-name">{{ currentUser }}</span>
        <span class="caret" :class="{ open }">▾</span>
      </button>
      <transition name="dropdown-fade">
        <div v-if="open" class="dropdown" @click.stop>
          <button class="dropdown-item" @click="goToInfo">
            <span class="dropdown-icon">👤</span>
            用户信息
          </button>
          <button class="dropdown-item danger" @click="logout">
            <span class="dropdown-icon">🚪</span>
            登出
          </button>
        </div>
      </transition>
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
const currentUser = ref('用户')

const visible = computed(() => {
  const p = route.path || ''
  return !(p === '/' || /login/i.test(p) || /welcome/i.test(p));
})

function toggle() {
  open.value = !open.value
}

function toggleSidebar() {
  window.dispatchEvent(new CustomEvent('toggle-sidebar'))
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
      sessionStorage.removeItem('currentUser')
      sessionStorage.removeItem('userRole')
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
  window.addEventListener('storage', updateUserFromStorage)
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
    background: linear-gradient(90deg, #2b6cb0, #4a90e2);
    color: #fff;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    z-index: 1001;
    backdrop-filter: blur(8px);
  }

  .left {
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  .hamburger-btn {
    display: none;
    background: transparent;
    border: none;
    color: #fff;
    font-size: 1.5rem;
    cursor: pointer;
    padding: 0.5rem;
    border-radius: 6px;
    transition: background 0.2s ease;
    line-height: 1;
  }

  .hamburger-btn:hover {
    background: rgba(255, 255, 255, 0.1);
  }

  .hamburger-btn:active {
    background: rgba(255, 255, 255, 0.15);
  }

  .hamburger-icon {
    display: block;
    font-size: 1.5rem;
  }

  .brand {
    font-weight: 700;
    font-size: 1.1rem;
    cursor: pointer;
    transition: opacity 0.2s ease;
    white-space: nowrap;
  }

  .brand:hover {
    opacity: 0.9;
  }

  .right {
    position: relative;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .menu-btn {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    background: rgba(255, 255, 255, 0.1);
    border: 0;
    color: #fff;
    padding: 0.5rem 0.8rem;
    border-radius: 8px;
    cursor: pointer;
    font-weight: 600;
    transition: all 0.2s ease;
    backdrop-filter: blur(4px);
  }

  .menu-btn:hover {
    background: rgba(255, 255, 255, 0.15);
    transform: translateY(-1px);
  }

  .user-name {
    max-width: 150px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .caret {
    transition: transform 0.2s ease;
    display: inline-block;
    font-size: 0.9rem;
  }

  .caret.open {
    transform: rotate(180deg);
  }

  .dropdown {
    position: absolute;
    right: 0;
    top: calc(100% + 12px);
    background: #fff;
    color: #222;
    border-radius: 10px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12), 0 2px 6px rgba(0, 0, 0, 0.08);
    overflow: hidden;
    min-width: 180px;
    border: 1px solid rgba(0, 0, 0, 0.05);
  }

  .dropdown-item {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    width: 100%;
    padding: 0.75rem 1rem;
    text-align: left;
    background: transparent;
    border: none;
    cursor: pointer;
    font-weight: 600;
    transition: background 0.15s ease;
    color: #374151;
  }

  .dropdown-item:hover {
    background: #f3f6fb;
  }

  .dropdown-item.danger {
    color: #dc2626;
  }

  .dropdown-item.danger:hover {
    background: #fef2f2;
  }

  .dropdown-icon {
    font-size: 1.1rem;
  }

  /* 下拉菜单动画 */
  .dropdown-fade-enter-active,
  .dropdown-fade-leave-active {
    transition: all 0.2s ease;
  }

  .dropdown-fade-enter-from,
  .dropdown-fade-leave-to {
    opacity: 0;
    transform: translateY(-8px);
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .hamburger-btn {
      display: block;
    }

    .topbar {
      padding: 0 0.75rem;
    }

    .brand {
      font-size: 1rem;
    }

    .user-name {
      max-width: 100px;
    }

    .menu-btn {
      padding: 0.4rem 0.6rem;
    }
  }

  @media (max-width: 480px) {
    .brand {
      font-size: 0.9rem;
    }

    .user-name {
      display: none;
    }

    .menu-btn {
      padding: 0.4rem;
      min-width: 40px;
      justify-content: center;
    }

    .dropdown {
      min-width: 150px;
    }
  }
</style>