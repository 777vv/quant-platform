<template>
  <el-dialog
    :model-value="modelValue"
    :title="`编辑标签 · ${fundCode}`"
    width="480px"
    @update:model-value="emit('update:modelValue', $event)"
    @open="load"
  >
    <el-empty v-if="library.length === 0" :image-size="60" description="标签库为空，请先在「标签管理」中创建标签" />
    <template v-else>
      <el-checkbox-group v-model="selected">
        <el-checkbox v-for="tag in library" :key="tag.id" :value="tag.id" class="tag-option">
          {{ tag.name }}
        </el-checkbox>
      </el-checkbox-group>
      <div class="muted tag-hint">可多选；标签属于预定义库，如需新维度请到「标签管理」创建。</div>
    </template>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="library.length === 0" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fundTags, setFundTags, tagLibrary, type FundTagVO } from '@/api/fund'

/**
 * 给单只基金贴标签：从预定义标签库中多选，覆盖式保存（清空即移除该基金全部标签）。
 */
const props = defineProps<{
  modelValue: boolean
  /** 目标基金代码 */
  fundCode: string
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 保存成功（父组件刷新标签展示） */
  (e: 'saved'): void
}>()

const library = ref<FundTagVO[]>([])
const selected = ref<number[]>([])
const saving = ref(false)

async function load() {
  library.value = await tagLibrary()
  const current = await fundTags(props.fundCode)
  selected.value = current.tags.map((t) => t.id)
}

async function save() {
  saving.value = true
  try {
    await setFundTags(props.fundCode, selected.value)
    ElMessage.success('标签已保存')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.tag-option {
  margin-right: var(--q-space-4);
}

.tag-hint {
  margin-top: var(--q-space-3);
  line-height: 1.6;
}
</style>
