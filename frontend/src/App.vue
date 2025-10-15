<script setup lang="ts">
import Sidebar from './components/Sidebar.vue'
import Topbar from './components/Topbar.vue'
import {useRoute} from "vue-router";
import {computed} from "vue";


const route = useRoute()
const shouldShowBars = computed(() => {
  const p = route.path || ''
  return !(p === '/' || /login/i.test(p) || /welcome/i.test(p))
})
</script>

<template>
  <div class="app-layout">
    <Sidebar />
    <div class="content-wrapper">
      <Topbar />
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>.app-layout {
  display: flex;
  min-height: 100vh;
}

.content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding-top: v-bind('shouldShowBars ? "56px" : "0"');
  padding-left: v-bind('shouldShowBars ? "220px" : "0"');
}

main.main-content {
  flex: 1;
  padding: 0; /* 移除内边距 */
  background: #f8fafc;
  margin-top: 0;
  height: 100%; /* 确保高度为100% */
}


</style>