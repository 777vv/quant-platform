<template>
  <div class="user-center">
    <!-- 第一行：账号相关两张半宽卡（高度接近，视觉成对） -->
    <el-row :gutter="12">
      <el-col :span="12" :xs="24">
        <el-card shadow="never" class="fill-height">
          <template #header>
            <div class="card-header"><span>基本信息</span></div>
          </template>
          <el-descriptions :column="2" border size="small" class="info-desc">
            <el-descriptions-item label="用户名">{{ profile?.username }}</el-descriptions-item>
            <el-descriptions-item label="最后登录">{{ formatTime(profile?.lastLoginAt) }}</el-descriptions-item>
          </el-descriptions>
          <el-form :model="profileForm" label-width="90px" class="card-form">
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" maxlength="64" placeholder="界面展示昵称" />
            </el-form-item>
            <el-form-item label="通知邮箱">
              <el-input v-model="profileForm.email" maxlength="128" placeholder="信号摘要邮件收件人" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12" :xs="24">
        <el-card shadow="never" class="fill-height">
          <template #header>
            <div class="card-header">
              <span>安全设置</span>
              <span class="muted">修改成功后需重新登录</span>
            </div>
          </template>
          <el-form :model="passwordForm" label-width="90px" class="card-form">
            <el-form-item label="原密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="off" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="off" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="off" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPassword" @click="savePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：AI 模型配置通栏（V4.0：每个厂商各存一份，选中 + 保存 = 切换厂商） -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>AI 模型配置</span>
          <div class="header-status">
            <!-- 正在编辑的厂商不是"当前使用"的那家时点明一句：避免把表单状态误读成平台状态 -->
            <span v-if="activeProvider && activeProvider.code !== modelForm.provider" class="muted">
              当前使用：{{ activeProvider.name }}（保存后才切换）
            </span>
            <!-- 配置是异步载入的：加一层 v-if="modelConfig" 避免载入前先闪一下"未配置" -->
            <template v-if="modelConfig">
              <el-tag v-if="modelConfig.active" type="success" size="small">当前使用中</el-tag>
              <el-tag v-else-if="modelConfig.configured" type="info" size="small">已配置（未使用）</el-tag>
              <el-tag v-else type="warning" size="small">未配置</el-tag>
            </template>
          </div>
        </div>
      </template>
      <el-alert
        v-if="modelConfig && !modelConfig.configured"
        type="info"
        :closable="false"
        class="model-hint"
        :title="modelConfig.hint || '该厂商尚未配置：填写 Token 并选择模型后保存，即切换为当前使用的厂商'"
      />
      <el-form :model="modelForm" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8" :xs="24">
            <el-form-item label="厂商">
              <el-select v-model="modelForm.provider" style="width: 100%" @change="onProviderChange">
                <el-option
                  v-for="item in providers"
                  :key="item.code"
                  :value="item.code"
                  :label="providerLabel(item)"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16" :xs="24">
            <el-form-item label="Base URL">
              <el-input v-model="modelForm.baseUrl" placeholder="OpenAI 兼容端点，如 https://open.bigmodel.cn/api/paas/v4" />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="Token">
              <el-input
                v-model="modelForm.apiKey"
                type="password"
                show-password
                autocomplete="off"
                :placeholder="tokenPlaceholder"
              />
            </el-form-item>
          </el-col>
          <el-col :span="16" :xs="24">
            <el-form-item label="模型">
              <div class="model-row">
                <el-select
                  v-model="modelForm.model"
                  filterable
                  allow-create
                  default-first-option
                  placeholder="点右侧拉取，或直接输入模型名"
                  style="flex: 1"
                >
                  <el-option
                    v-for="item in modelOptions"
                    :key="item.id"
                    :value="item.id"
                    :label="item.free ? `${item.id}（免费）` : item.id"
                  />
                </el-select>
                <el-button :loading="loadingModels" @click="fetchModels">拉取模型列表</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 单价：按厂商各存一份（各厂价目不同；额度是全局的，在【AI用量统计】页维护） -->
        <el-divider content-position="left">单价（按厂商各存一份；单位：元/百万 token；0 = 不计算费用）</el-divider>
        <el-row :gutter="16">
          <el-col :span="8" :xs="24">
            <el-form-item label="输入单价">
              <el-input-number
                v-model="modelForm.inputPrice"
                :min="0"
                :precision="4"
                :controls="false"
                placeholder="元/百万 token"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="缓存单价">
              <el-input-number
                v-model="modelForm.cacheInputPrice"
                :min="0"
                :precision="4"
                :controls="false"
                placeholder="命中缓存的输入价，0 = 按输入单价"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="输出单价">
              <el-input-number
                v-model="modelForm.outputPrice"
                :min="0"
                :precision="4"
                :controls="false"
                placeholder="元/百万 token"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="form-note">
          <b>每个厂商各存一份配置</b>（Base URL / 模型 / Token / 单价），切换厂商不会覆盖别家已存的配置；
          下拉里标「当前使用」的就是对话实际调用的那家，选中别家再点【保存并生效】即完成切换
          （Token 显示「已配置」时留空不改，切回旧厂商无需重填）。切换厂商会自动填入该厂商默认地址，
          自建/代理地址可直接改 Base URL；「免费」按内置清单标注，以厂商官网定价为准。
          <br />
          每日额度（token/费用上限、预警线）是<b>全局一份</b>，与厂商无关，在【AI用量统计】页维护。
        </div>
        <el-form-item class="form-actions">
          <el-button type="primary" :loading="savingModel" @click="saveModel">保存并生效</el-button>
          <el-button :loading="testingModel" @click="testModel">连通性自检</el-button>
          <span v-if="modelConfig?.updatedAt" class="muted">
            该厂商配置最近修改 {{ formatTime(modelConfig.updatedAt) }}
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 第三行：邮件通知通栏（V4.9 配置入库：可编辑表单 + 保存即生效） -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>邮件通知</span>
          <el-tag :type="mail?.enabled ? 'success' : 'info'" size="small">
            {{ mail?.enabled ? '已开启' : '未开启' }}
          </el-tag>
        </div>
      </template>
      <el-form :model="mailForm" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8" :xs="24">
            <el-form-item label="SMTP 服务器">
              <el-input v-model="mailForm.host" placeholder="如 smtp.qq.com，留空 = 不启用邮件" />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="端口">
              <el-input-number v-model="mailForm.port" :min="1" :max="65535" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="通知开关">
              <el-switch v-model="mailForm.enabled" active-text="开启" inactive-text="关闭" />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="发件账号">
              <el-input v-model="mailForm.username" placeholder="SMTP 登录邮箱，如 xxx@qq.com" />
            </el-form-item>          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="授权码">
              <el-input
                v-model="mailForm.password"
                type="password"
                show-password
                autocomplete="off"
                :placeholder="mailPasswordPlaceholder"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="发件人">
              <el-input v-model="mailForm.fromAddr" placeholder="留空 = 用发件账号，如 wang<xxx@qq.com>" />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="收件人">
              <el-input v-model="mailForm.toAddr" placeholder="留空 = 用上方资料中的通知邮箱" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="form-note">
          配置保存在本平台数据库，<b>保存即生效，无需重启</b>；发件账号与授权码显示为打码值（如
          83***@qq.com）时表示沿用已存配置，<b>不改动即保持原值</b>，只在首次填写或更换时重填。 「通知
          开关」控制每日信号摘要与同步告警是否自动发送（关闭后仍可手动发测试邮件）。
        </div>
        <el-form-item class="form-actions">
          <el-button type="primary" :loading="savingMail" @click="saveMail">保存邮件配置</el-button>
          <el-button type="primary" plain :loading="testing" :disabled="!mailForm.host || !mailForm.username" @click="sendTestMail">
            发送测试邮件
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getProfile, updatePassword, updateProfile } from '@/api/auth'
import { mailConfig, saveMailConfig, testMail, type MailConfigVO } from '@/api/notify'
import {
  aiModelConfig,
  aiModelList,
  aiModelProviders,
  saveAiModelConfig,
  testAiModelConfig,
  type AiModelConfigVO,
  type AiModelOptionVO,
  type AiProviderVO
} from '@/api/ai'
import type { UserVO } from '@/types/api'

const router = useRouter()

const profile = ref<UserVO | null>(null)
const mail = ref<MailConfigVO | null>(null)
const savingMail = ref(false)
/** 邮件表单（V4.9 入库可编辑；password 留空 = 保持原授权码） */
const mailForm = reactive({
  enabled: false,
  host: '',
  port: 465,
  username: '',
  password: '',
  fromAddr: '',
  toAddr: ''
})

/** AI 模型配置：厂商清单（含各厂商状态）、可选模型、当前表单对应的厂商配置 */
const providers = ref<AiProviderVO[]>([])
const modelOptions = ref<AiModelOptionVO[]>([])
const modelConfig = ref<AiModelConfigVO | null>(null)
const loadingModels = ref(false)
const savingModel = ref(false)
const testingModel = ref(false)
/** 模型表单：单价为 V4.0 归到厂商维度的字段（额度已挪到【AI用量统计】页，全局一份） */
const modelForm = reactive({
  provider: 'ZHIPU',
  baseUrl: '',
  model: '',
  apiKey: '',
  inputPrice: 0 as number | null,
  cacheInputPrice: 0 as number | null,
  outputPrice: 0 as number | null
})

/** 厂商下拉文案：把"当前使用/已配置"直接写进选项，切厂商时一眼看清会切到哪家 */
function providerLabel(item: AiProviderVO): string {
  const suffix = item.active ? '（当前使用）' : item.configured ? '（已配置）' : ''
  return `${item.name}${suffix}`
}

/**
 * 加载厂商清单与某个厂商的配置（不传 = 当前启用的厂商）。
 * @param provider 厂商代码；切换厂商时传它，即把该厂商已存的配置读进表单
 */
async function loadModelConfig(provider?: string) {
  const [providerList, config] = await Promise.all([aiModelProviders(), aiModelConfig(provider)])
  providers.value = providerList
  modelConfig.value = config
  modelForm.provider = config.provider
  modelForm.baseUrl = config.baseUrl
  modelForm.model = config.model
  modelForm.apiKey = ''
  modelForm.inputPrice = config.inputPrice ?? 0
  modelForm.cacheInputPrice = config.cacheInputPrice ?? 0
  modelForm.outputPrice = config.outputPrice ?? 0
  // 顺手把该厂商已保存的模型放进下拉，避免"已保存的模型不在选项里"的观感
  modelOptions.value = config.model ? [{ id: config.model, free: false }] : []
}

/**
 * 切换厂商：把该厂商**已存的配置**载入表单（不是清空重填）。
 *
 * <p>此时还没有切换"当前使用的厂商"——要点了【保存并生效】才切过去（用户拍板口径），
 * 所以误点下拉不会把对话搞挂。
 */
async function onProviderChange(code: string) {
  await loadModelConfig(code)
  const item = providers.value.find((provider) => provider.code === code)
  if (item && !item.configured) {
    ElMessage.info(`${item.name} 尚未配置：填写 Token 与模型后保存，即切换为当前使用的厂商`)
  }
}

/** 当前选中的厂商展示名 */
const currentProviderName = computed(
  () => providers.value.find((item) => item.code === modelForm.provider)?.name ?? modelForm.provider
)

/** 当前**正在使用**的厂商（清单里标 active 的那条；用于在编辑别家时点明实际在用谁） */
const activeProvider = computed(() => providers.value.find((item) => item.active) ?? null)

/** Token 框提示：该厂商已配置则说明"留空不改"，未配置就提示粘贴对应厂商的 Key */
const tokenPlaceholder = computed(() => {
  const saved = modelConfig.value
  if (saved?.hasKey && saved.provider === modelForm.provider) {
    return `已配置（${saved.keyMasked}），留空不改`
  }
  return `粘贴 ${currentProviderName.value} 的 API Key`
})

/** 拉取模型列表（用当前表单里的端点与 Token；该厂商未填 Token 时后端会回落到它已存的那份） */
async function fetchModels() {
  loadingModels.value = true
  try {
    modelOptions.value = await aiModelList({
      baseUrl: modelForm.baseUrl,
      apiKey: modelForm.apiKey || undefined,
      provider: modelForm.provider
    })
    ElMessage.success(`已拉取 ${modelOptions.value.length} 个模型`)
  } finally {
    loadingModels.value = false
  }
}

/** 保存该厂商配置并生效（保存即切为当前使用厂商，无需重启应用） */
async function saveModel() {
  savingModel.value = true
  try {
    await saveAiModelConfig({
      provider: modelForm.provider,
      baseUrl: modelForm.baseUrl,
      model: modelForm.model,
      apiKey: modelForm.apiKey,
      inputPrice: modelForm.inputPrice,
      cacheInputPrice: modelForm.cacheInputPrice,
      outputPrice: modelForm.outputPrice
    })
    ElMessage.success(`已保存并切换为：${currentProviderName.value}`)
    await loadModelConfig(modelForm.provider)
    // 厂商状态（当前使用/已配置）会变，重新拉一份清单
    providers.value = await aiModelProviders()
  } finally {
    savingModel.value = false
  }
}

/** 连通性自检：不改配置，只试一句最小请求（会记一行自检用量） */
async function testModel() {
  testingModel.value = true
  try {
    const result = await testAiModelConfig({
      baseUrl: modelForm.baseUrl,
      apiKey: modelForm.apiKey || undefined,
      model: modelForm.model
    })
    ElMessage.success(result)
  } finally {
    testingModel.value = false
  }
}
const savingProfile = ref(false)
const savingPassword = ref(false)
const testing = ref(false)

const profileForm = reactive({ nickname: '', email: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

/** 时间展示（yyyy-MM-dd HH:mm） */
function formatTime(time?: string): string {
  return time ? time.replace('T', ' ').substring(0, 16) : '--'
}

async function loadProfile() {
  profile.value = await getProfile()
  profileForm.nickname = profile.value.nickname ?? ''
  profileForm.email = profile.value.email ?? ''
}

async function loadMail() {
  mail.value = await mailConfig()
  mailForm.enabled = mail.value.enabled
  mailForm.host = mail.value.host ?? ''
  mailForm.port = mail.value.port ?? 465
  mailForm.username = mail.value.username ?? ''
  mailForm.password = ''
  mailForm.fromAddr = mail.value.from ?? ''
  mailForm.toAddr = mail.value.to ?? ''
}

/** 授权码占位：已配置时提示留空不改 */
const mailPasswordPlaceholder = computed(() => (mail.value?.username ? '已配置，留空保持原授权码' : 'SMTP 授权码（QQ/163 为授权码而非登录密码）'))

/** 保存邮件配置（保存即生效，无需重启） */
async function saveMail() {
  savingMail.value = true
  try {
    await saveMailConfig({
      enabled: mailForm.enabled,
      host: mailForm.host,
      port: mailForm.port,
      username: mailForm.username,
      password: mailForm.password || undefined,
      fromAddr: mailForm.fromAddr || undefined,
      toAddr: mailForm.toAddr || undefined
    })
    ElMessage.success('邮件配置已保存并生效')
    await loadMail()
  } finally {
    savingMail.value = false
  }
}

/** 保存昵称/通知邮箱 */
async function saveProfile() {
  if (profileForm.email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(profileForm.email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  savingProfile.value = true
  try {
    await updateProfile({ nickname: profileForm.nickname, email: profileForm.email })
    ElMessage.success('资料已保存')
    await loadProfile()
    await loadMail()
  } finally {
    savingProfile.value = false
  }
}

/** 修改密码：成功后清除本地 token 并跳登录页 */
async function savePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPassword.value = true
  try {
    await updatePassword({ ...passwordForm })
    ElMessage.success('密码已修改，请重新登录')
    localStorage.removeItem('quant_token')
    router.push('/login')
  } finally {
    savingPassword.value = false
  }
}

/** 发送测试邮件 */
async function sendTestMail() {
  testing.value = true
  try {
    await testMail()
    ElMessage.success(`测试邮件已发送至 ${mail.value?.to ?? ''}`)
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  loadModelConfig()
  loadProfile()
  loadMail()
})
</script>

<style scoped>
/* 页面骨架：卡片纵向统一 12px 节奏（不再各自加 margin-top） */
.user-center {
  display: flex;
  flex-direction: column;
  gap: var(--q-space-3);
  /* 宽屏下居中且几乎铺满：上限取 1400px（超过则两侧留等宽小边距），
     避免"左对齐 + 右侧一大块空白"的观感 */
  max-width: 1400px;
  margin: 0 auto;
}

/* 同一行两张卡等高，避免左右参差 */
.user-center :deep(.el-row) {
  row-gap: var(--q-space-3);
}

.fill-height {
  height: 100%;
}

/* 卡片标题：与仪表盘/基金池一致（品牌色短竖条 + 右侧次要信息） */
.card-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: var(--q-space-3);
  font-size: var(--q-font-base);
  font-weight: 600;
  color: var(--q-text-primary);
}

.card-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 14px;
  border-radius: 1px;
  background: var(--q-color-primary);
  transform: translateY(-50%);
}

.card-header .muted {
  font-weight: 400;
}

/* 卡片头右侧：状态说明与状态标签横向并排（避免 space-between 把它们摊开） */
.header-status {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
}

/* 描述表与表单的统一留白：描述表与表单之间、表单与卡片底之间 */
.info-desc {
  margin-bottom: var(--q-space-3);
}

.card-form :deep(.el-form-item:last-child),
.form-actions {
  margin-bottom: 0;
}

/* 模型行：下拉自适应 + 按钮固定宽度 */
.model-row {
  display: flex;
  gap: var(--q-space-2);
  width: 100%;
}

.model-hint {
  margin-bottom: var(--q-space-3);
}

/* 表单整体说明：横跨整行的弱化文案 */
.form-note {
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
  line-height: 1.6;
  margin-bottom: var(--q-space-3);
}

.mail-actions {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  margin-top: var(--q-space-3);
}

.mail-tip {
  margin-top: var(--q-space-3);
}
</style>
