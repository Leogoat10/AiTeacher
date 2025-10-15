<script setup lang="ts">
import Sidebar from './components/Sidebar.vue'
import Topbar from './components/Topbar.vue'
import { useRoute } from "vue-router"
import { computed } from "vue"

const route = useRoute()
const shouldShowBars = computed(() => {
  const p = route.path || ''
  return !(p === '/' || /login/i.test(p) || /welcome/i.test(p))
})
</script>

<template>
  <div class="app-layout">
    <Topbar />
    <Sidebar />
    <main class="main-content" :class="{ 'with-bars': shouldShowBars }">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f8fafc;
}

.main-content {
  flex: 1;
  min-height: 100vh;
  background: #f8fafc;
  transition: all 0.3s ease;
}

/* 当显示顶栏和侧边栏时的布局 */
.main-content.with-bars {
  padding-top: 56px;
  padding-left: 220px;
  min-height: calc(100vh - 56px);
}

/* 响应式布局 */
@media (max-width: 1024px) {
  .main-content.with-bars {
    padding-left: 200px;
  }
}

@media (max-width: 768px) {
  .main-content.with-bars {
    padding-left: 0;
  }
}

/* 确保内容区域可以正常滚动 */
.main-content {
  overflow-x: hidden;
  overflow-y: auto;
}
</style>