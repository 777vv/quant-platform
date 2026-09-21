<template>
  <div>
    <template v-if="type === 'GRID'">
      <el-form-item label="网格模式">
        <el-radio-group v-model="form.mode">
          <el-radio-button value="arithmetic">等差</el-radio-button>
          <el-radio-button value="geometric">等比</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="网格上沿">
        <el-input-number v-model="form.upper" :precision="3" :min="0.001" :controls="false" />
      </el-form-item>
      <el-form-item label="网格下沿">
        <el-input-number v-model="form.lower" :precision="3" :min="0.001" :controls="false" />
      </el-form-item>
      <el-form-item label="格数">
        <el-input-number v-model="form.grids" :min="2" :max="100" :controls="false" />
      </el-form-item>
      <el-form-item label="每格份额">
        <el-input-number v-model="form.sharePerGrid" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item label="底仓份额">
        <el-input-number v-model="form.basePosition" :min="0" :controls="false" />
      </el-form-item>
    </template>
    <template v-else-if="type === 'VAL_PERCENTILE'">
      <el-form-item label="低估阈值%">
        <el-input-number v-model="form.lowPct" :precision="1" :min="1" :max="49" :controls="false" />
      </el-form-item>
      <el-form-item label="高估阈值%">
        <el-input-number v-model="form.highPct" :precision="1" :min="51" :max="99" :controls="false" />
      </el-form-item>
      <el-form-item label="分档数">
        <el-input-number v-model="form.steps" :min="1" :max="10" :controls="false" />
      </el-form-item>
      <el-form-item label="回看窗口(年)">
        <el-input-number v-model="form.windowYears" :min="1" :max="30" :controls="false" />
      </el-form-item>
      <el-form-item label="每档份额">
        <el-input-number v-model="form.sharePerStep" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item label="底仓份额">
        <el-input-number v-model="form.basePosition" :min="0" :controls="false" />
      </el-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{ type: string; modelValue: Record<string, unknown> }>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const defaults: Record<string, Record<string, unknown>> = {
  GRID: { mode: 'arithmetic', upper: 5.5, lower: 3.0, grids: 10, sharePerGrid: 2000, basePosition: 4000 },
  VAL_PERCENTILE: { lowPct: 25, highPct: 75, steps: 5, windowYears: 8, sharePerStep: 3000, basePosition: 5000 }
}

const form = reactive<Record<string, unknown>>({ ...(defaults[props.type] ?? {}), ...props.modelValue })

watch(
  () => props.type,
  (type) => {
    Object.keys(form).forEach((key) => delete form[key])
    Object.assign(form, defaults[type] ?? {})
    emit('update:modelValue', { ...form })
  }
)

watch(
  form,
  () => {
    emit('update:modelValue', { ...form })
  },
  { deep: true }
)
</script>
