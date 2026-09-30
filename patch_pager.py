# -*- coding: utf-8 -*-
"""一次性补丁：市场信号三页签独立分页 + 每页条数可选。跑完即删。"""
import io

p = 'web/src/views/signal/MarketSignalView.vue'
s = io.open(p, encoding='utf-8').read()

# ===== ① 脚本：共享 page/PAGE_SIZE → 三页签独立状态 =====
old = '''// ===== 分页（每页 10 条，与持仓列表同模式）：数据量小、三个页签共用一份计算结果，故前端分页 =====
const PAGE_SIZE = 10
const page = ref(1)'''
new = '''// ===== 分页（V5.51 用户口径：每页条数可自选；三个页签的页码与条数各自独立，互不影响）=====
const PAGE_SIZES = [10, 20, 50, 100]
const chgPage = ref(1)
const chgSize = ref(10)
const valPage = ref(1)
const valSize = ref(10)
const premiumPage = ref(1)
const premiumSize = ref(10)'''
assert old in s, '① 分页状态未匹配'
s = s.replace(old, new, 1)

# ===== ② 切片函数带页参 =====
old = '''function paged(list: MarketSignalRow[]): MarketSignalRow[] {
  return list.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE)
}

/** 涨跌榜：全量排序 → 切当前页 */
const chgRows = computed(() => paged(sortByState(filteredRows.value, chgSort.value)))
/** 估值红绿灯：无排序列，直接切当前页 */
const valRows = computed(() => paged(filteredRows.value))
/** 技术面与溢价：全量排序 → 切当前页 */
const premiumRows = computed(() => paged(sortByState(filteredRows.value, premiumSortState.value)))'''
new = '''function paged(list: MarketSignalRow[], pageNumber: number, sizeNumber: number): MarketSignalRow[] {
  return list.slice((pageNumber - 1) * sizeNumber, pageNumber * sizeNumber)
}

/** 涨跌榜：全量排序 → 切当前页 */
const chgRows = computed(() => paged(sortByState(filteredRows.value, chgSort.value), chgPage.value, chgSize.value))
/** 估值红绿灯：无排序列，直接切当前页 */
const valRows = computed(() => paged(filteredRows.value, valPage.value, valSize.value))
/** 技术面与溢价：全量排序 → 切当前页 */
const premiumRows = computed(() => paged(sortByState(filteredRows.value, premiumSortState.value), premiumPage.value, premiumSize.value))'''
assert old in s, '② 切片未匹配'
s = s.replace(old, new, 1)

# ===== ③ 页码回位：三页签全部回第 1 页 =====
old = '''// 筛选/换数据后页码回位，防越界空页（V5.36 持仓列表同款处理）
watch(selectedTags, () => {
  page.value = 1
})'''
new = '''// 筛选/换数据后所有页签页码回位，防越界空页（V5.36 持仓列表同款处理）
watch(selectedTags, resetPages)
watch(selectedFund, resetPages)

function resetPages() {
  chgPage.value = 1
  valPage.value = 1
  premiumPage.value = 1
}'''
assert old in s, '③ 回位未匹配'
s = s.replace(old, new, 1)

# load() 里的 page.value = 1 → 三页签回位
old = '''    rows.value = data.rows
    page.value = 1'''
new = '''    rows.value = data.rows
    resetPages()'''
assert old in s, 'load 回位未匹配'
s = s.replace(old, new, 1)

# ===== ④ 模板：三处分页器各自绑定独立状态 + sizes =====
old = '''            <el-table-column label="价格数据截至" align="center" min-width="100">
              <template #default="{ row }">{{ row.lastDate ?? '—' }}</template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination v-model:current-page="page" :page-size="PAGE_SIZE" :total="filteredRows.length" layout="total, prev, pager, next" background />
          </div>
        </el-tab-pane>'''
new = '''            <el-table-column label="价格数据截至" align="center" min-width="100">
              <template #default="{ row }">{{ row.lastDate ?? '—' }}</template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination v-model:current-page="chgPage" v-model:page-size="chgSize" :page-sizes="PAGE_SIZES" :total="filteredRows.length" layout="total, sizes, prev, pager, next" background />
          </div>
        </el-tab-pane>'''
assert old in s, '④ 涨跌榜分页器未匹配'
s = s.replace(old, new, 1)

old = '''            <el-table-column label="PE数据截至" align="center" min-width="100">
              <template #default="{ row }">{{ row.peDate ?? '—' }}</template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination v-model:current-page="page" :page-size="PAGE_SIZE" :total="filteredRows.length" layout="total, prev, pager, next" background />
          </div>
        </el-tab-pane>'''
new = '''            <el-table-column label="PE数据截至" align="center" min-width="100">
              <template #default="{ row }">{{ row.peDate ?? '—' }}</template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination v-model:current-page="valPage" v-model:page-size="valSize" :page-sizes="PAGE_SIZES" :total="filteredRows.length" layout="total, sizes, prev, pager, next" background />
          </div>
        </el-tab-pane>'''
assert old in s, '④ 估值分页器未匹配'
s = s.replace(old, new, 1)

old = '''          <div class="pager-row">
            <el-pagination v-model:current-page="page" :page-size="PAGE_SIZE" :total="filteredRows.length" layout="total, prev, pager, next" background />
          </div>
        </el-tab-pane>
      </el-tabs>'''
new = '''          <div class="pager-row">
            <el-pagination v-model:current-page="premiumPage" v-model:page-size="premiumSize" :page-sizes="PAGE_SIZES" :total="filteredRows.length" layout="total, sizes, prev, pager, next" background />
          </div>
        </el-tab-pane>
      </el-tabs>'''
assert old in s, '④ 技术面分页器未匹配'
s = s.replace(old, new, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('OK 三页签独立分页 + 每页条数可选完成')
