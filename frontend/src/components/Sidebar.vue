<script setup lang="ts">
  import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
  import { useRoute } from 'vue-router'
  import axios from 'axios'

  const route = useRoute()

  type Role = 'guest' | 'teacher' | 'student'
  const role = ref<Role>('guest')

  const teacherItems = [
    { label: '教师信息', path: '/teacherInfo' },
    { label: '课程管理', path: '/teacherCourses' },
    { label: 'AI出题', path: '/TeacherQuestion' },
    { label: 'AI教案', path: '/ai/lesson' },
  ]

  const studentItems = [
    { label: '学生信息', path: '/studentInfo' },
    { label: '题目通知', path: '/studentNotifications' },
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
      // 先检查教师身份，成功则直接设置为 teacher 并返回（避免触发 studentInfo 的 401）
      const teacherResp = await axios.get('/api/Info/teacherInfo', { withCredentials: true, validateStatus: () => true })
      if (teacherResp.status === 200) {
        role.value = 'teacher'
        return
      }

      // 仅在不是教师时再检查学生身份
      const studentResp = await axios.get('/api/Info/studentInfo', { withCredentials: true, validateStatus: () => true })
      if (studentResp.status === 200) {
        role.value = 'student'
      } else {
        role.value = 'guest'
      }
    } catch (e) {
      role.value = 'guest'
    } finally {
      // 防抖
      setTimeout(() => { running = false }, 200)
    }
  }

  onMounted(() => {
    detectRole()
    window.addEventListener('auth-changed', detectRole)
  })

  onUnmounted(() => {
    window.removeEventListener('auth-changed', detectRole)
  })

  watch(() => route.path, () => {
    detectRole()
  })
  </script>

  <template>
    <aside v-if="visible" class="sidebar">
      <nav>
        <router-link
          v-for="item in items"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
        >
          <span class="label">{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>
  </template>

  <style scoped>
  .sidebar {
    width: 220px;
    position: fixed;
    top: 0;
    left: 0;
    height: 100vh;
    z-index: 1000;
    background: #1f2937;
    color: #e6eef8;
    padding-top: calc(56px + 1rem);
    box-sizing: border-box;
  }
  nav {
    display: flex;
    flex-direction: column;
  }
  .nav-item {
    padding: 0.85rem 1.25rem;
    color: #cbd5e1;
    text-decoration: none;
    transition: background 0.15s, color 0.15s;
  }
  .nav-item:hover {
    background: rgba(255,255,255,0.04);
    color: #fff;
  }
  .nav-item.active {
    background: linear-gradient(90deg, rgba(100,108,255,0.12), rgba(66,184,131,0.06));
    color: #fff;
    font-weight: 600;
  }
  .label {
    display: inline-block;
  }
  </style>