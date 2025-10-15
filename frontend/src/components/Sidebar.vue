<script setup lang="ts">
  import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
  import { useRoute } from 'vue-router'
  import axios from 'axios'

  const route = useRoute()

  type Role = 'guest' | 'teacher' | 'student'
  const role = ref<Role>('guest')

  // 新增：控制侧边栏折叠状态
  const isCollapsed = ref(false)
  
  // 检测是否为移动端
  const isMobile = ref(false)
  
  // 新增：导出给父组件使用
  defineExpose({ isCollapsed })

  const teacherItems = [
    { label: '教师信息', path: '/teacherInfo', icon: '👤' },
    { label: '课程管理', path: '/teacherCourses', icon: '📚' },
    { label: 'AI出题', path: '/TeacherQuestion', icon: '✍️' },
    { label: 'AI教案', path: '/ai/lesson', icon: '📝' },
  ]

  const studentItems = [
    { label: '学生信息', path: '/studentInfo', icon: '👤' },
    { label: '我的题目', path: '/studentAssignments', icon: '📚' },
  ]

  const items = computed(() => (role.value === 'student' ? studentItems : teacherItems))

  const visible = computed(() => {
    const p = route.path || ''
    return !(['/', '/welcome', '/login', '/teacherLogin', '/studentLogin'].includes(p) || /login/i.test(p))
  })

  let running = false
  async function detectRole() {
    if (running) return
    running = true
    try {
      const teacherResp = await axios.get('/api/Info/teacherInfo', { withCredentials: true, validateStatus: () => true })
      if (teacherResp.status === 200) {
        role.value = 'teacher'
        return
      }

      const studentResp = await axios.get('/api/Info/studentInfo', { withCredentials: true, validateStatus: () => true })
      if (studentResp.status === 200) {
        role.value = 'student'
      } else {
        role.value = 'guest'
      }
    } catch (e) {
      role.value = 'guest'
    } finally {
      setTimeout(() => { running = false }, 200)
    }
  }

  // 监听全局折叠事件
  function handleToggleSidebar() {
    isCollapsed.value = !isCollapsed.value
  }

  // 响应式：在小屏幕上默认折叠
  function checkScreenSize() {
    isMobile.value = window.innerWidth < 768
    if (isMobile.value) {
      isCollapsed.value = true
    }
  }

  onMounted(() => {
    detectRole()
    checkScreenSize()
    window.addEventListener('auth-changed', detectRole)
    window.addEventListener('toggle-sidebar', handleToggleSidebar)
    window.addEventListener('resize', checkScreenSize)
  })

  onUnmounted(() => {
    window.removeEventListener('auth-changed', detectRole)
    window.removeEventListener('toggle-sidebar', handleToggleSidebar)
    window.removeEventListener('resize', checkScreenSize)
  })

  watch(() => route.path, () => {
    detectRole()
    // 移动端点击导航后自动收起
    if (isMobile.value) {
      isCollapsed.value = true
    }
  })
</script>

<template>
  <aside v-if="visible" class="sidebar" :class="{ collapsed: isCollapsed }">
    <nav>
      <router-link
        v-for="item in items"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        :class="{ active: route.path === item.path }"
      >
        <span class="icon">{{ item.icon }}</span>
        <span class="label">{{ item.label }}</span>
      </router-link>
    </nav>
  </aside>
  <!-- 移动端遮罩层 -->
  <div 
    v-if="visible && !isCollapsed && isMobile" 
    class="sidebar-overlay"
    @click="isCollapsed = true"
  ></div>
</template>

<style scoped>
.sidebar {
  width: 220px;
  position: fixed;
  top: 56px;
  left: 0;
  height: calc(100vh - 56px);
  z-index: 999;
  background: #1f2937;
  color: #e6eef8;
  padding-top: 1rem;
  box-sizing: border-box;
  transition: transform 0.3s ease, width 0.3s ease;
  overflow-x: hidden;
  overflow-y: auto;
}

.sidebar.collapsed {
  transform: translateX(-100%);
}

nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0 0.5rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  color: #cbd5e1;
  text-decoration: none;
  border-radius: 8px;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.nav-item:hover {
  background: rgba(255,255,255,0.08);
  color: #fff;
  transform: translateX(2px);
}

.nav-item.active {
  background: linear-gradient(90deg, rgba(100,108,255,0.18), rgba(66,184,131,0.1));
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(100,108,255,0.2);
}

.icon {
  font-size: 1.2rem;
  flex-shrink: 0;
}

.label {
  flex: 1;
}

/* 移动端遮罩 */
.sidebar-overlay {
  position: fixed;
  top: 56px;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 998;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .sidebar {
    width: 200px;
  }
}

@media (max-width: 768px) {
  .sidebar {
    width: 260px;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
  }
  
  .sidebar.collapsed {
    transform: translateX(-100%);
  }
  
  .sidebar:not(.collapsed) {
    transform: translateX(0);
  }
}

/* 滚动条样式 */
.sidebar::-webkit-scrollbar {
  width: 6px;
}

.sidebar::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
}

.sidebar::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>