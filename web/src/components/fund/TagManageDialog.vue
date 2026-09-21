<template>
  <el-dialog
    :model-value="modelValue"
    title="标签管理"
    width="560px"
    @update:model-value="emit('update:modelValue', $event)"
    @open="load"
  >
    <div class="tag-create">
      <el-input
        v-model="newName"
        size="small"
        maxlength="32"
        placeholder="输入新标签名，如 红利 / 宽基 / 债券"
        @keyup.enter="create"
      />
      <el-button size="small" type="primary" :loading="saving" @click="create">新建标签</el-button>
    </div>

    <el-table v-loading="loading" :data="tags" size="small" class="tag-table">
      <el-table-column label="标签" min-width="160">
        <template #default="{ row }">
          <template v-if="editingId === row.id">
            <el-input v-model="editingName" size="small" maxlength="32" @keyup.enter="saveRename" />
          </template>
          <span v-else>{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="已贴基金" width="100" align="right">
        <template #default="{ row }">
          <span class="num">{{ row.fundCount ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="right">
        <template #default="{ row }">
          <template v-if="editingId === row.id">
            <el-button size="small" type="primary" link @click="saveRename">保存</el-button>
            <el-button size="small" link @click="editingId = null">取消</el-button>
          </template>
          <template v-else>
            <el-button size="small" link @click="startRename(row)">重命名</el-button>
            <el-button size="small" type="danger" link @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="muted tag-hint">
      标签为预定义库：先在此建立，再到基金详情页或自选列表贴到具体基金上，可避免“红利 / 红利策略 / 高股息”这类同义碎片。
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTag, deleteTag, renameTag, tagLibrary, type FundTagVO } from '@/api/fund'

/**
 * 标签管理（预定义标签库的增删改）：删除标签会同时解除其与基金的关联（二次确认并提示影响范围）。
 */
defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 标签库变更（父组件刷新列表展示） */
  (e: 'changed'): void
}>()

const tags = ref<FundTagVO[]>([])
const loading = ref(false)
const saving = ref(false)
const newName = ref('')
const editingId = ref<number | null>(null)
const editingName = ref('')

async function load() {
  loading.value = true
  try {
    tags.value = await tagLibrary()
  } finally {
    loading.value = false
  }
}

async function create() {
  const name = newName.value.trim()
  if (!name) {
    ElMessage.warning('请输入标签名')
    return
  }
  saving.value = true
  try {
    await createTag(name)
    ElMessage.success(`标签「${name}」已创建`)
    newName.value = ''
    await load()
    emit('changed')
  } finally {
    saving.value = false
  }
}

function startRename(row: FundTagVO) {
  editingId.value = row.id
  editingName.value = row.name
}

async function saveRename() {
  const name = editingName.value.trim()
  if (!name || editingId.value === null) {
    ElMessage.warning('请输入标签名')
    return
  }
  await renameTag(editingId.value, name)
  ElMessage.success('已重命名')
  editingId.value = null
  await load()
  emit('changed')
}

async function remove(row: FundTagVO) {
  const count = row.fundCount ?? 0
  await ElMessageBox.confirm(
    count > 0
      ? `删除标签「${row.name}」会同时解除它与 ${count} 只基金的关联，基金数据不受影响。确认删除？`
      : `确认删除标签「${row.name}」？`,
    '删除标签',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await deleteTag(row.id)
  ElMessage.success('已删除')
  await load()
  emit('changed')
}
</script>

<style scoped>
.tag-create {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  margin-bottom: var(--q-space-3);
}

.tag-table {
  margin-bottom: var(--q-space-3);
}

.tag-hint {
  line-height: 1.6;
}
</style>
