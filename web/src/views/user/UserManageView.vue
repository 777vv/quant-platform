<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="section-title">用户管理</span>
          <div class="header-actions">
            <span class="muted">临时账号可见范围与可写操作由你逐个勾选，停用立即踢下线</span>
            <el-button type="primary" size="small" @click="openCreate">新增临时账号</el-button>
          </div>
        </div>
      </template>

      <el-table :data="users" v-loading="loading" size="small" row-key="id">
        <el-table-column prop="username" label="用户名" min-width="110" />
        <el-table-column prop="nickname" label="昵称" min-width="100" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'ADMIN'" type="primary" size="small">管理员</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">临时</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限" min-width="200">
          <template #default="{ row }">
            <template v-if="row.role === 'ADMIN'">全部权限</template>
            <el-tooltip v-else-if="row.permissions.length" :content="row.permissions.join('、')" placement="top">
              <span class="perm-summary">{{ row.permissions.length }} 项（{{ shortPerms(row.permissions) }}）</span>
            </el-tooltip>
            <span v-else class="muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch
              v-if="row.role !== 'ADMIN'"
              :model-value="row.enabled === 1"
              @change="(value: string | number | boolean) => onToggleStatus(row, value as boolean)"
            />
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column label="有效期至" width="110">
          <template #default="{ row }">
            <span v-if="row.expiresAt" class="num">{{ row.expiresAt.substring(0, 10) }}</span>
            <span v-else class="muted">永久</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '--' }}</template>
        </el-table-column>
        <el-table-column label="最后登录" width="160">
          <template #default="{ row }">
            <span v-if="row.lastLoginAt" class="num">{{ row.lastLoginAt.replace('T', ' ').substring(0, 19) }}</span>
            <span v-else class="muted">从未登录</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="right">
          <template #default="{ row }">
            <template v-if="row.role !== 'ADMIN'">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link size="small" @click="openReset(row)">重置密码</el-button>
              <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
            </template>
            <span v-else class="muted">内置账号</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑弹窗：权限分「可见菜单 / 允许操作」两组勾选 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑临时账号' : '新增临时账号'" width="640px">
      <el-form label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!editingId" placeholder="字母/数字/下划线" style="width: 220px" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" style="width: 220px" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" required>
          <el-input v-model="form.password" placeholder="6~32 位，创建后可自行修改" style="width: 220px" show-password />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker
            v-model="form.expiresOn"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="不选 = 永久有效"
            style="width: 220px"
          />
          <span class="field-hint">到期后账号无法登录、在线会话立即失效</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="给谁用的、为什么开" style="width: 460px" />
        </el-form-item>
        <el-form-item label="可见菜单">
          <el-checkbox-group v-model="form.permissions">
            <el-checkbox v-for="option in menuOptions" :key="option.code" :value="option.code">{{ option.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="允许操作">
          <el-checkbox-group v-model="form.permissions">
            <el-checkbox v-for="option in actionOptions" :key="option.code" :value="option.code">{{ option.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="快捷模板">
          <el-button size="small" @click="applyGuestDefault">一键只读访客</el-button>
          <el-button size="small" @click="form.permissions = []">清空全部</el-button>
          <span class="field-hint">只读模板 = 仪表盘 + 基金池 + 信号查询 + 基金对比 + 使用手册</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetVisible" title="重置密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="账号">{{ resetTarget?.username }}</el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="resetPassword" show-password placeholder="6~32 位" style="width: 220px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onResetSave">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createUser,
  deleteUser,
  guestDefault,
  listUsers,
  permissionOptions,
  resetUserPassword,
  updateUser,
  updateUserStatus
} from '@/api/userManage'
import type { PermissionOption, UserManageItem } from '@/api/userManage'

const users = ref<UserManageItem[]>([])
const options = ref<PermissionOption[]>([])
const loading = ref(false)
const saving = ref(false)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ username: '', nickname: '', password: '', expiresOn: '', remark: '', permissions: [] as string[] })

const resetVisible = ref(false)
const resetTarget = ref<UserManageItem | null>(null)
const resetPassword = ref('')

/** 菜单组 / 操作组选项（来自后端权限字典） */
const menuOptions = computed(() => options.value.filter((option) => option.group === 'menu'))
const actionOptions = computed(() => options.value.filter((option) => option.group === 'action'))

/** 权限摘要：最多展示前 2 个码的分组名后缀，完整清单看悬浮提示 */
function shortPerms(permissions: string[]): string {
  return permissions
    .slice(0, 2)
    .map((code) => code.split(':')[1])
    .join('、') + (permissions.length > 2 ? '…' : '')
}

async function loadAll() {
  loading.value = true
  try {
    users.value = await listUsers()
    if (options.value.length === 0) {
      options.value = await permissionOptions()
    }
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { username: '', nickname: '', password: '', expiresOn: '', remark: '', permissions: [] }
  dialogVisible.value = true
  // 打开即预填「只读访客」模板（用户拍板的默认值），需要更多再勾
  guestDefault().then((codes) => {
    if (!editingId.value) {
      form.value.permissions = [...codes]
    }
  })
}

/** 一键只读访客模板（勾选面板快捷按钮）：拉后端默认码覆盖当前勾选 */
async function applyGuestDefault() {
  form.value.permissions = [...(await guestDefault())]
}

function openEdit(row: UserManageItem) {
  editingId.value = row.id
  form.value = {
    username: row.username,
    nickname: row.nickname,
    password: '',
    expiresOn: row.expiresAt ? row.expiresAt.substring(0, 10) : '',
    remark: row.remark ?? '',
    permissions: [...row.permissions]
  }
  dialogVisible.value = true
}

/** 保存（创建或编辑）；提交体字段与后端请求 DTO 逐一对齐 */
async function onSave() {
  if (!editingId.value && !form.value.username.trim()) {
    ElMessage.warning('请填写用户名')
    return
  }
  if (!editingId.value && form.value.password.length < 6) {
    ElMessage.warning('初始密码至少 6 位')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateUser(editingId.value, {
        nickname: form.value.nickname,
        permissions: form.value.permissions,
        expiresOn: form.value.expiresOn || undefined,
        remark: form.value.remark
      })
      ElMessage.success('已保存，权限即时生效')
    } else {
      await createUser({
        username: form.value.username.trim(),
        nickname: form.value.nickname || form.value.username.trim(),
        password: form.value.password,
        permissions: form.value.permissions,
        expiresOn: form.value.expiresOn || undefined,
        remark: form.value.remark
      })
      ElMessage.success('已创建临时账号')
    }
    dialogVisible.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

/** 启用/停用（停用立即踢下线） */
async function onToggleStatus(row: UserManageItem, value: boolean) {
  await updateUserStatus(row.id, value ? 1 : 0)
  ElMessage.success(value ? '已启用' : '已停用并踢下线')
  await loadAll()
}

function openReset(row: UserManageItem) {
  resetTarget.value = row
  resetPassword.value = ''
  resetVisible.value = true
}

async function onResetSave() {
  if (!resetTarget.value || resetPassword.value.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  saving.value = true
  try {
    await resetUserPassword(resetTarget.value.id, resetPassword.value)
    ElMessage.success('已重置，该账号已全端下线')
    resetVisible.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: UserManageItem) {
  await ElMessageBox.confirm(`确认删除账号「${row.username}」？删除后立即下线且不可恢复。`, '删除确认', {
    type: 'warning'
  })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
}

.section-title {
  font-weight: 600;
  color: var(--q-text-primary);
}

.muted {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.perm-summary {
  font-size: var(--q-font-sm);
  color: var(--q-text-regular);
}

.field-hint {
  margin-left: var(--q-space-3);
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}
</style>
