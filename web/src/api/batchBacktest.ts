/**
 * 批量回测接口（V5.96）：批次列表 / 详情（读）+ 发起批次（写）。
 * 每只基金的结果仍是一条 backtest_record（带 batch_id），结果页/删除/应用复用既有接口。
 */
import { get, post } from './request'
import type { PageResult } from '@/types/api'
import type { BacktestRecord } from './strategy'

/** 批量回测批次（含实时进度计数） */
export interface BacktestBatch {
  id: number
  /** 策略类型码（整批统一） */
  strategyType: string
  /** 策略参数快照 JSON */
  params: string
  startDate: string
  endDate: string
  /** 统一初始资金（手填模式；null = 按基金自动算，各记录行有自己的 initialCapital） */
  initialCapital: number | null
  /** 基金总数 */
  totalCount: number
  /** 成功数（每完成一只即累加） */
  successCount: number
  /** 失败数 */
  failCount: number
  /** 0=运行中 1=已完成 */
  status: number
  /** 操作账号 */
  createdBy: string | null
  /** 发起时间（操作时间） */
  createdAt: string
  /** 完成时间 */
  finishedAt: string | null
}

/** 批次详情：批次 + 全部基金回测记录（不含曲线列） */
export interface BacktestBatchDetail {
  batch: BacktestBatch
  records: BacktestRecord[]
}

/** 发起批量回测请求 */
export interface BatchBacktestRequest {
  strategyType: string
  params: Record<string, unknown>
  startDate: string
  endDate: string
  /** 统一初始资金；留空 = 按基金自动算 */
  initialCapital?: number | null
  fundCodes: string[]
}

/** 批次分页（按发起时间倒序；strategyType 过滤用于"回填该策略上一次批量配置"） */
export function batchBacktestPage(page = 1, size = 10, strategyType?: string) {
  return get<PageResult<BacktestBatch>>('/backtest-batch', { page, size, strategyType: strategyType || undefined })
}

/** 批次详情（批次口径 + 全部基金回测记录） */
export function batchBacktestDetail(id: number) {
  return get<BacktestBatchDetail>(`/backtest-batch/${id}`)
}

/** 发起批量回测（异步执行，返回批次 ID） */
export function createBatchBacktest(data: BatchBacktestRequest) {
  return post<number>('/backtest-batch', data)
}
