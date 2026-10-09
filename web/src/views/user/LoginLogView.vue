<template>
  <div class="login-log">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>登录日志</span>
          <span class="muted">记录每次登录尝试（成功与失败都记）：IP 已按反向代理链取真实来源，UA 与 traceId 便于排查</span>
        </div>
      </template>

      <div class="filter-row">
        <span class="muted">筛选</span>
        <el-input
          v-model="username"
          placeholder="用户名"
          clearable
          style="width: 160px"
          @keyup.enter="reload()"
        />
        <el-select v-model="successFilter" clearable placeholder="全部结果" style="width: 130px" @change="reload()">
          <el-option :value="1" label="仅成功" />
          <el-option :value="0" label="仅失败" />
        </el-select>
        <el-button @click="reload()">查询</el-button>
        <span class="muted">共 {{ total }} 条</span>
      </div>

      <el-table v-loading="loading" :data="rows" size="small">
        <el-table-column label="时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="结果" min-width="76" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="100" show-overflow-tooltip />
        <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.failReason ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="IP" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="归属地" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ipLocation ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="客户端" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ shortUa(row.userAgent) }}</template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination
          v-model:current-page="page"
          :page-size="PAGE_SIZE"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { loginLogs, type LoginLogItem } from '@/api/auth'

const loading = ref(false)
const rows = ref<LoginLogItem[]>([])
const total = ref(0)
const page = ref(1)
const username = ref('')
const successFilter = ref<number | undefined>(undefined)

/** 每页条数（全站统一 10 条/页） */
const PAGE_SIZE = 10

const usernameKeyword = computed(() => username.value.trim() || undefined)

/** 登录时间到秒（安全审计需要精确时间，与列表页"到分"的展示口径不同） */
function formatTime(time: string | null): string {
  return time ? time.replace('T', ' ').substring(0, 19) : '—'
}

/** UA 只展示关键部分：完整串很长，取前 60 字并把括号内容省略 */
function shortUa(ua: string | null): string {
  if (!ua) {
    return '—'
  }
  return ua.length > 60 ? `${ua.substring(0, 60)}…` : ua
}

async function load() {
  loading.value = true
  try {
    const result = await loginLogs({
      username: usernameKeyword.value,
      success: successFilter.value,
      page: page.value,
      size: PAGE_SIZE
    })
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

/** 改筛选条件后回到第 1 页再查（防越界空页） */
function reload() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.login-log {
  width: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  padding-left: var(--q-space-3);
  font-size: var(--q-font-base);
  font-weight: 600;
  color: var(--q-text-primary);
}

.card-header::before {
  content: '';
  flex: none;
  width: 3px;
  height: 14px;
  border-radius: 1px;
  background: var(--q-color-primary);
}

.card-header > span:first-of-type {
  flex: none;
  white-space: nowrap;
}

.card-header .muted {
  font-weight: 400;
}

.muted {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  margin-bottom: var(--q-space-2);
}

.pager-row {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--q-space-2);
}
</style>
