<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Film,
  Tickets,
  VideoPlay,
  Refresh,
  Search,
  Grid,
  List,
  CopyDocument,
  Lock,
  Unlock,
  Check,
  CircleClose,
  View,
  DataLine,
  WarningFilled,
  Clock,
  Connection,
} from '@element-plus/icons-vue'
import {
  fetchContentCacheStatus,
  fetchShelfBooks,
  fetchBookEpisodes,
  fetchPublicBookDetail,
  probeEpisodeVideo,
  refreshContentShelf,
} from '../services/adminApi'
import type {
  ContentCacheStatus,
  ContentBook,
  ContentEpisode,
  ContentVideoProbe,
} from '../services/adminApi'

interface EpisodeProbeState {
  status: 'idle' | 'probing' | 'success' | 'error'
  videoUrl?: string
  duration?: number
  ttfbMs?: number
  host?: string
  protocol?: string
  error?: string
}

const activeTab = ref('dramas')
const loading = ref(false)
const booksLoading = ref(false)
const refreshing = ref(false)
const error = ref('')
const status = ref<ContentCacheStatus | null>(null)

// 货架选择
const shelfType = ref('recommend')
const shelfOptions = [
  { label: '首页推荐 (recommend)', value: 'recommend' },
  { label: '新剧上市 (new-release)', value: 'new-release' },
  { label: '热播配音 (drama-dub)', value: 'drama-dub' },
]

// 短剧片库与视图切换
const books = ref<ContentBook[]>([])
const searchQuery = ref('')
const viewMode = ref<'grid' | 'table'>('grid')

// 过滤后的短剧列表
const filteredBooks = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return books.value
  }
  return books.value.filter(
    (b) =>
      b.title.toLowerCase().includes(query) ||
      b.bookId.toLowerCase().includes(query) ||
      b.filteredTitle.toLowerCase().includes(query) ||
      (b.description && b.description.toLowerCase().includes(query)),
  )
})

// 分集与审片监看台
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const selectedBook = ref<ContentBook | null>(null)
const selectedEpisode = ref<ContentEpisode | null>(null)
const episodes = ref<ContentEpisode[]>([])
const episodeSearch = ref('')
const freeEpisodes = ref(7) // 平台前 7 集免费，第 8 集起锁 VIP
const episodeViewMode = ref<'matrix' | 'table'>('matrix')
const heatmapFilter = ref<'all' | 'error' | 'untested' | 'vip'>('all')

// 探针质检字典：按集数缓存状态
const probeMap = reactive<Record<number, EpisodeProbeState>>({})

// 批量全季巡检状态
const batchProbing = ref(false)
const batchCurrent = ref(0)
const batchTotal = ref(0)
const cancelBatch = ref(false)

const batchProgressPercent = computed(() => {
  if (batchTotal.value === 0) return 0
  return Math.min(100, Math.round((batchCurrent.value / batchTotal.value) * 100))
})

const totalFailedCount = computed(() => {
  return episodes.value.filter((ep) => probeMap[ep.episode]?.status === 'error').length
})

const totalSuccessCount = computed(() => {
  return episodes.value.filter((ep) => probeMap[ep.episode]?.status === 'success').length
})

const displayedEpisodes = computed(() => {
  let list = episodes.value
  const q = episodeSearch.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (ep) =>
        ep.episode.toString() === q ||
        ep.title.toLowerCase().includes(q) ||
        ep.chapterId.toLowerCase().includes(q),
    )
  }
  if (heatmapFilter.value === 'error') {
    return list.filter((ep) => probeMap[ep.episode]?.status === 'error')
  }
  if (heatmapFilter.value === 'untested') {
    return list.filter((ep) => !probeMap[ep.episode] || probeMap[ep.episode].status === 'idle')
  }
  if (heatmapFilter.value === 'vip') {
    return list.filter((ep) => ep.episode > freeEpisodes.value)
  }
  return list
})

// 当前选中的播放探针结果
const currentActiveProbe = computed<EpisodeProbeState | null>(() => {
  if (!selectedEpisode.value) return null
  return probeMap[selectedEpisode.value.episode] || null
})

// <video> 自己能拿到 URL 却播不了：Chrome 不支持 m3u8（TwistyTV 的 rs_ 片源就是 HLS）、
// 编码不被支持，或者自动播放/网络被拦。上游地址没有扩展名，只能看元素状态。
const videoElementError = ref(false)
const videoStuck = ref(false)
const videoState = ref('')
let videoWatchdog: number | undefined

function readVideoState(): string {
  const el = document.querySelector<HTMLVideoElement>('.studio-video-element')
  if (!el) return '播放器未挂载'
  return [
    `${el.videoWidth || 0}×${el.videoHeight || 0}`,
    `readyState=${el.readyState}`,
    `networkState=${el.networkState}`,
    `currentTime=${el.currentTime.toFixed(1)}`,
    el.error ? `error=${el.error.code}${el.error.message ? ` (${el.error.message})` : ''}` : 'error=none',
  ].join(' · ')
}

function resetVideoState() {
  videoElementError.value = false
  videoStuck.value = false
  videoState.value = ''
  if (videoWatchdog !== undefined) window.clearTimeout(videoWatchdog)
}

watch(
  () => currentActiveProbe.value?.videoUrl,
  (url) => {
    resetVideoState()
    if (!url) return
    // 4 秒还没出画面就把元素状态摆出来，否则只能看到一片黑，分不清是编码、自动播放还是网络。
    videoWatchdog = window.setTimeout(() => {
      const el = document.querySelector<HTMLVideoElement>('.studio-video-element')
      if (!el) return
      videoState.value = readVideoState()
      if (el.currentTime === 0) videoStuck.value = true
    }, 4000)
  },
)

// 错误详情弹窗
const errorDetailVisible = ref(false)
const currentErrorText = ref('')
const currentErrorTitle = ref('')

function openErrorDetail(title: string, text: string) {
  currentErrorTitle.value = title
  currentErrorText.value = text
  errorDetailVisible.value = true
}

async function copyText(text: string, label = '内容') {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制到剪贴板`)
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

async function loadStatus() {
  loading.value = true
  error.value = ''
  try {
    status.value = await fetchContentCacheStatus()
  } catch {
    error.value = '内容缓存状态加载失败'
  } finally {
    loading.value = false
  }
}

async function loadShelfBooks(type: string) {
  booksLoading.value = true
  try {
    const list = await fetchShelfBooks(type)
    books.value = list
  } catch {
    ElMessage.error(`获取【${type}】货架片库失败`)
    books.value = []
  } finally {
    booksLoading.value = false
  }
}

async function handleShelfChange(newType: string) {
  searchQuery.value = ''
  await loadShelfBooks(newType)
}

async function handleRefreshShelf() {
  const shelfName = shelfOptions.find((o) => o.value === shelfType.value)?.label ?? shelfType.value
  try {
    await ElMessageBox.confirm(
      `确定强制从内容源拉取并更新【${shelfName}】片库缓存吗？此操作将耗时数秒至数十秒。`,
      '确认刷新货架',
      {
        confirmButtonText: '立即刷新',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  refreshing.value = true
  try {
    const result = await refreshContentShelf(shelfType.value, 'en')
    ElMessage.success(`货架【${shelfName}】刷新成功，已同步 ${(result as unknown[])?.length || 0} 部短剧`)
    await Promise.all([loadStatus(), loadShelfBooks(shelfType.value)])
  } catch {
    ElMessage.error('货架刷新失败，请检查上游数据源连通性')
  } finally {
    refreshing.value = false
  }
}

async function openBookDetail(book: ContentBook) {
  selectedBook.value = book
  drawerVisible.value = true
  drawerLoading.value = true
  episodes.value = []
  selectedEpisode.value = null
  episodeSearch.value = ''
  heatmapFilter.value = 'all'
  cancelBatch.value = false
  batchProbing.value = false

  // 清空上部剧的探针结果
  Object.keys(probeMap).forEach((key) => {
    delete probeMap[Number(key)]
  })

  try {
    const epList = await fetchBookEpisodes(book.bookId, book.filteredTitle)
    episodes.value = epList.sort((a, b) => a.episode - b.episode)
    // 货架快照里的简介经常为空（/browse 卡片不带简介）。刚拉分集时后端已把详情页的
    // 简介补进缓存，这里补读一次就能立刻显示，不必等下一轮货架刷新。
    if (!selectedBook.value.description?.trim()) {
      fetchPublicBookDetail(book.bookId)
        .then((detail) => {
          if (selectedBook.value?.bookId === detail.bookId) {
            selectedBook.value = { ...selectedBook.value, ...detail }
          }
        })
        .catch(() => undefined)
    }
    if (episodes.value.length > 0) {
      // 默认选中第一集并直接自动探测
      selectedEpisode.value = episodes.value[0]
      probeSingleEpisode(episodes.value[0])
    }
  } catch {
    ElMessage.error('分集数据拉取失败')
  } finally {
    drawerLoading.value = false
  }
}

async function probeSingleEpisode(ep: ContentEpisode): Promise<void> {
  if (!selectedBook.value) return
  const epNum = ep.episode
  selectedEpisode.value = ep

  // 如果已经探测成功，直接展示无需重复请求
  if (probeMap[epNum]?.status === 'success') {
    return
  }

  probeMap[epNum] = { status: 'probing' }
  const t0 = performance.now()

  try {
    const res: ContentVideoProbe = await probeEpisodeVideo(
      selectedBook.value.bookId,
      epNum,
      selectedBook.value.filteredTitle,
      ep.chapterId,
    )
    const ttfbMs = Math.round(performance.now() - t0)

    let host = '未知'
    let protocol = 'MP4'
    try {
      const u = new URL(res.videoUrl)
      host = u.hostname
      const ext = u.pathname.split('?')[0].split('.').pop()?.toUpperCase()
      if (ext) protocol = ext
    } catch {
      // url parse fallback
    }

    probeMap[epNum] = {
      status: 'success',
      videoUrl: res.videoUrl,
      duration: res.duration,
      ttfbMs,
      host,
      protocol,
    }
  } catch {
    const ttfbMs = Math.round(performance.now() - t0)
    probeMap[epNum] = {
      status: 'error',
      error: '上游无法提供播放流 (404/502 或未转码)',
      ttfbMs,
    }
  }
}

// 一键全季并发巡检（受控并发度 3）
async function startBatchProbe() {
  if (!selectedBook.value || episodes.value.length === 0) return
  batchProbing.value = true
  cancelBatch.value = false
  batchTotal.value = episodes.value.length
  batchCurrent.value = 0

  const queue = [...episodes.value]
  const concurrency = 3

  async function worker() {
    while (queue.length > 0 && !cancelBatch.value) {
      const ep = queue.shift()
      if (!ep) break
      await probeSingleEpisode(ep)
      batchCurrent.value++
    }
  }

  const workers = Array.from({ length: Math.min(concurrency, queue.length) }, () => worker())
  await Promise.all(workers)

  batchProbing.value = false
  if (cancelBatch.value) {
    ElMessage.info('已中断全季巡检')
  } else {
    ElMessage.success(
      `全季巡检完成：成功 ${totalSuccessCount.value} 集，断流失效 ${totalFailedCount.value} 集`,
    )
  }
}

function stopBatchProbe() {
  cancelBatch.value = true
}

function formatDuration(seconds?: number) {
  if (!seconds || seconds <= 0) return '未知时长'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  if (m === 0) return `${s} 秒`
  return `${m} 分 ${s > 0 ? `${s} 秒` : ''} (${seconds}s)`
}

function shelfHealthType(health: string) {
  if (health === 'HEALTHY') return 'success'
  if (health === 'ERROR') return 'danger'
  return 'warning'
}

watch(shelfType, (newType) => {
  handleShelfChange(newType)
})

onMounted(async () => {
  await Promise.all([loadStatus(), loadShelfBooks(shelfType.value)])
})
</script>

<template>
  <section v-loading="loading" class="page-section">
    <!-- 顶部工作栏 -->
    <div class="page-header">
      <div>
        <div class="title-with-badge">
          <h1>视频内容管理</h1>
          <el-tag effect="plain" type="success" size="small" class="live-pill">
            <span class="pulse-dot"></span> 运行正常
          </el-tag>
        </div>
        <p>短剧资产查看、货架编排流转、分集试看探针与缓存健康监控</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="shelfType" class="shelf-select" placeholder="选择货架">
          <el-option
            v-for="option in shelfOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button
          :loading="refreshing"
          type="primary"
          :icon="Refresh"
          @click="handleRefreshShelf"
        >
          刷新当前货架
        </el-button>
        <el-button :icon="Refresh" @click="() => { loadStatus(); loadShelfBooks(shelfType); }">
          刷新大盘
        </el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" show-icon type="error" />

    <!-- 顶部业务指标卡 -->
    <div class="metric-grid">
      <div class="metric-card metric-primary">
        <div class="metric-header">
          <div class="metric-label">短剧资产索引</div>
          <el-icon class="metric-icon"><Film /></el-icon>
        </div>
        <div class="metric-value">{{ status?.bookCount ?? 0 }}</div>
        <div class="metric-footer">PostgreSQL 权威短剧库</div>
      </div>
      <div class="metric-card metric-success">
        <div class="metric-header">
          <div class="metric-label">分集元数据缓存</div>
          <el-icon class="metric-icon"><Tickets /></el-icon>
        </div>
        <div class="metric-value">{{ status?.episodeCacheCount ?? 0 }}</div>
        <div class="metric-footer">剧集全量章节与分集索引</div>
      </div>
      <div class="metric-card metric-warning">
        <div class="metric-header">
          <div class="metric-label">播放地址探针缓存</div>
          <el-icon class="metric-icon"><VideoPlay /></el-icon>
        </div>
        <div class="metric-value">{{ status?.videoCacheCount ?? 0 }}</div>
        <div class="metric-footer">上游故障短 TTL 兜底缓冲</div>
      </div>
    </div>

    <!-- 核心视图标签页 -->
    <el-card shadow="never" class="workbench-card">
      <el-tabs v-model="activeTab" class="custom-tabs">
        <!-- Tab 1: 货架短剧资产 -->
        <el-tab-pane label="货架短剧资产" name="dramas">
          <div class="dramas-toolbar">
            <div class="dramas-filter">
              <el-input
                v-model="searchQuery"
                placeholder="按剧名 / ID / 过滤标题搜索..."
                :prefix-icon="Search"
                clearable
                class="search-input"
              />
              <span class="count-badge">
                当前货架：<b>{{ books.length }}</b> 部短剧
                <template v-if="searchQuery">
                  （筛选出 <b>{{ filteredBooks.length }}</b> 部）
                </template>
              </span>
            </div>
            <div class="view-switch">
              <el-radio-group v-model="viewMode" size="small">
                <el-radio-button value="grid">
                  <el-icon><Grid /></el-icon> 海报网格
                </el-radio-button>
                <el-radio-button value="table">
                  <el-icon><List /></el-icon> 数据列表
                </el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <!-- 海报网格视图 -->
          <div v-if="viewMode === 'grid'" v-loading="booksLoading" class="poster-grid-container">
            <el-empty
              v-if="!booksLoading && filteredBooks.length === 0"
              description="当前货架暂无短剧数据或未匹配到关键词"
            />
            <div v-else class="poster-grid">
              <div
                v-for="book in filteredBooks"
                :key="book.bookId"
                class="poster-card"
                @click="openBookDetail(book)"
              >
                <div class="poster-cover-wrapper">
                  <el-image
                    :src="book.coverUrl"
                    loading="lazy"
                    fit="cover"
                    class="poster-image"
                  >
                    <template #error>
                      <div class="image-fallback">
                        <el-icon><Film /></el-icon>
                        <span>暂无封面</span>
                      </div>
                    </template>
                    <template #placeholder>
                      <div class="image-placeholder">加载中...</div>
                    </template>
                  </el-image>
                  <div class="episode-badge">{{ book.chapterCount }} 集</div>
                </div>
                <div class="poster-content">
                  <div class="poster-title" :title="book.title">{{ book.title }}</div>
                  <div class="poster-id mono" :title="book.bookId">ID: {{ book.bookId }}</div>
                  <div class="poster-desc" :title="book.description || '暂无简介'">
                    {{ book.description || '暂无简介' }}
                  </div>
                  <div class="poster-actions">
                    <el-button
                      type="primary"
                      link
                      size="small"
                      :icon="View"
                      @click.stop="openBookDetail(book)"
                    >
                      分集监看台
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 表格视图 -->
          <div v-else v-loading="booksLoading" class="table-container">
            <el-table :data="filteredBooks" border stripe style="width: 100%">
              <el-table-column label="海报" width="90" align="center">
                <template #default="{ row }">
                  <el-image
                    :src="row.coverUrl"
                    fit="cover"
                    class="table-thumb"
                  >
                    <template #error>
                      <div class="table-thumb-fallback"><el-icon><Film /></el-icon></div>
                    </template>
                  </el-image>
                </template>
              </el-table-column>
              <el-table-column label="短剧名称" prop="title" min-width="180">
                <template #default="{ row }">
                  <div class="table-title-cell">
                    <span class="table-title-text">{{ row.title }}</span>
                    <span class="table-subtitle mono">{{ row.filteredTitle }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="Book ID" prop="bookId" width="180">
                <template #default="{ row }">
                  <div class="copyable-cell">
                    <span class="mono">{{ row.bookId }}</span>
                    <el-button
                      link
                      :icon="CopyDocument"
                      size="small"
                      @click="copyText(row.bookId, 'Book ID')"
                    />
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="集数" prop="chapterCount" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.chapterCount }} 集</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="剧集简介" prop="description" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="desc-text">{{ row.description || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140" fixed="right" align="center">
                <template #default="{ row }">
                  <el-button
                    type="primary"
                    size="small"
                    :icon="View"
                    @click="openBookDetail(row)"
                  >
                    分集监看台
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Tab 2: 货架与任务监控 -->
        <el-tab-pane label="货架健康与任务监控" name="monitoring">
          <div class="sub-section">
            <h3 class="section-heading">货架缓存健康状态</h3>
            <el-table :data="status?.shelves ?? []" border class="status-table">
              <el-table-column label="货架名称" prop="shelfType" width="160">
                <template #default="{ row }">
                  <span class="shelf-pill mono">{{ row.shelfType }}</span>
                </template>
              </el-table-column>
              <el-table-column label="语言" prop="locale" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ row.locale }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="健康状态" width="130" align="center">
                <template #default="{ row }">
                  <el-tag :type="shelfHealthType(row.health)">{{ row.health }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column align="right" label="已收录部数" prop="itemCount" width="120">
                <template #default="{ row }">
                  <b>{{ row.itemCount }}</b>
                </template>
              </el-table-column>
              <el-table-column label="最近刷新时间" width="200" prop="refreshedAt">
                <template #default="{ row }">
                  <span class="mono">{{ row.refreshedAt || '尚未刷新' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="健康说明" min-width="200" prop="healthMessage" />
              <el-table-column label="最近错误" min-width="240" prop="lastError">
                <template #default="{ row }">
                  <div v-if="row.lastError" class="error-cell">
                    <span class="error-text mono">{{ row.lastError }}</span>
                    <el-button
                      type="danger"
                      link
                      size="small"
                      @click="openErrorDetail('货架最近错误', row.lastError)"
                    >
                      详情
                    </el-button>
                  </div>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="sub-section" style="margin-top: 24px;">
            <h3 class="section-heading">最近刷新任务运行记录</h3>
            <el-table :data="status?.recentRefreshRuns ?? []" border class="status-table">
              <el-table-column label="触发来源" prop="triggerSource" width="120" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.triggerSource === 'ADMIN' ? 'warning' : 'info'">
                    {{ row.triggerSource }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="货架" prop="shelfType" width="140">
                <template #default="{ row }">
                  <span class="mono">{{ row.shelfType }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column align="right" label="拉取条数" prop="itemCount" width="100" />
              <el-table-column align="right" label="耗时 (ms)" prop="durationMillis" width="120">
                <template #default="{ row }">
                  <span class="mono">{{ row.durationMillis }} ms</span>
                </template>
              </el-table-column>
              <el-table-column label="开始时间" width="200" prop="startedAt">
                <template #default="{ row }">
                  <span class="mono">{{ row.startedAt }}</span>
                </template>
              </el-table-column>
              <el-table-column label="错误说明" min-width="220" prop="errorMessage">
                <template #default="{ row }">
                  <div v-if="row.errorMessage" class="error-cell">
                    <span class="error-text mono">{{ row.errorMessage }}</span>
                    <el-button
                      type="danger"
                      link
                      size="small"
                      @click="openErrorDetail('任务运行报错', row.errorMessage)"
                    >
                      详情
                    </el-button>
                  </div>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 短剧分集与审片监看台抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      title="短剧审片监看台与分集质检"
      size="840px"
      :destroy-on-close="true"
      class="detail-drawer"
    >
      <div v-if="selectedBook" v-loading="drawerLoading" class="drawer-content">
        <!-- 头部资产概览卡 -->
        <div class="book-header-card">
          <el-image
            :src="selectedBook.coverUrl"
            fit="cover"
            class="drawer-cover"
          >
            <template #error>
              <div class="drawer-cover-fallback"><el-icon><Film /></el-icon></div>
            </template>
          </el-image>
          <div class="book-header-info">
            <h2 class="book-main-title">{{ selectedBook.title }}</h2>
            <div class="book-meta-row">
              <span class="meta-tag mono">BookId: {{ selectedBook.bookId }}</span>
              <el-button
                link
                :icon="CopyDocument"
                size="small"
                @click="copyText(selectedBook.bookId, 'BookId')"
              />
              <span class="meta-sep">·</span>
              <span class="meta-tag">共 {{ selectedBook.chapterCount }} 集</span>
              <span class="meta-sep">·</span>
              <span class="meta-tag mono">{{ selectedBook.filteredTitle }}</span>
            </div>
            <div class="book-desc-box">
              {{ selectedBook.description || '暂无短剧简介' }}
            </div>
          </div>
        </div>

        <!-- Cinematic 暗色审片监看台 (Studio Monitor) -->
        <div class="cinematic-monitor">
          <div class="monitor-topbar">
            <div class="monitor-title">
              <span class="pulse-indicator"></span>
              <span class="monitor-label">实时监看视窗:</span>
              <span class="monitor-ep-text" v-if="selectedEpisode">
                EP {{ selectedEpisode.episode }} · {{ selectedEpisode.title || `第 ${selectedEpisode.episode} 集` }}
              </span>
              <span v-else class="monitor-ep-text">未选择分集</span>
            </div>
            <div class="monitor-actions" v-if="currentActiveProbe?.videoUrl">
              <el-button
                size="small"
                text
                class="dark-btn"
                :icon="CopyDocument"
                @click="copyText(currentActiveProbe.videoUrl, '播放流 URL')"
              >
                复制流地址
              </el-button>
            </div>
          </div>

          <div class="monitor-body">
            <!-- 播放器区域 -->
            <div class="video-stage">
              <template v-if="currentActiveProbe?.status === 'probing'">
                <div class="video-placeholder probing-glow">
                  <el-icon class="is-loading"><Refresh /></el-icon>
                  <span>正在向上游抓取该集并解析流媒体...</span>
                </div>
              </template>
              <template v-else-if="currentActiveProbe?.status === 'error'">
                <div class="video-placeholder error-glow">
                  <el-icon><WarningFilled /></el-icon>
                  <span>该集流媒体探测失败 (断流或上游 404/502)</span>
                  <div class="error-subtext">{{ currentActiveProbe.error }}</div>
                  <el-button
                    type="danger"
                    size="small"
                    style="margin-top: 8px;"
                    @click="selectedEpisode && probeSingleEpisode(selectedEpisode)"
                  >
                    重试探测
                  </el-button>
                </div>
              </template>
              <template v-else-if="currentActiveProbe?.status === 'success' && currentActiveProbe.videoUrl">
                <video
                  :key="currentActiveProbe.videoUrl"
                  :src="currentActiveProbe.videoUrl"
                  controls
                  muted
                  playsinline
                  autoplay
                  preload="metadata"
                  class="studio-video-element"
                  @loadeddata="videoElementError = false; videoStuck = false"
                  @pause="videoStuck = false"
                  @error="videoElementError = true; videoState = readVideoState()"
                >
                  您的浏览器不支持 HTML5 视频播放。
                </video>
                <div v-if="videoElementError || videoStuck" class="video-playback-hint">
                  <el-icon><WarningFilled /></el-icon>
                  <span v-if="videoElementError">
                    浏览器无法直接解码这条流（多为 HLS/m3u8 或特殊编码）。
                  </span>
                  <span v-else>4 秒内没有出画面，可能是自动播放被拦、网络不通或流本身有问题。</span>
                  <a :href="currentActiveProbe.videoUrl" target="_blank" rel="noreferrer">在新窗口打开</a>
                  <span class="video-playback-state">{{ videoState }}</span>
                </div>
              </template>
              <template v-else>
                <div class="video-placeholder idle-glow">
                  <el-icon><VideoPlay /></el-icon>
                  <span>点击下方任意集数格子开启流媒体质检</span>
                </div>
              </template>
            </div>

            <!-- 流媒体参数胶囊控制台 (Media Specs Capsule) -->
            <div class="specs-capsule-panel">
              <div class="specs-title">流媒体技术参数透视</div>
              <div class="specs-grid">
                <div class="spec-card">
                  <div class="spec-label"><el-icon><Clock /></el-icon> 响应延迟 (TTFB)</div>
                  <div class="spec-val">
                    <template v-if="currentActiveProbe?.ttfbMs !== undefined">
                      <span
                        class="mono latency-badge"
                        :class="{
                          'lat-fast': currentActiveProbe.ttfbMs < 400,
                          'lat-medium': currentActiveProbe.ttfbMs >= 400 && currentActiveProbe.ttfbMs < 1000,
                          'lat-slow': currentActiveProbe.ttfbMs >= 1000
                        }"
                      >
                        {{ currentActiveProbe.ttfbMs }} ms
                      </span>
                    </template>
                    <span v-else class="text-dim">-</span>
                  </div>
                </div>

                <div class="spec-card">
                  <div class="spec-label"><el-icon><Film /></el-icon> 标称权威时长</div>
                  <div class="spec-val mono">
                    {{ currentActiveProbe?.duration ? formatDuration(currentActiveProbe.duration) : '-' }}
                  </div>
                </div>

                <div class="spec-card">
                  <div class="spec-label"><el-icon><Connection /></el-icon> 封装协议</div>
                  <div class="spec-val mono">
                    <el-tag size="small" type="info" effect="dark">
                      {{ currentActiveProbe?.protocol || 'MP4' }}
                    </el-tag>
                  </div>
                </div>

                <div class="spec-card">
                  <div class="spec-label"><el-icon><DataLine /></el-icon> 门禁规则</div>
                  <div class="spec-val">
                    <span v-if="selectedEpisode && selectedEpisode.episode <= freeEpisodes" class="free-text">
                      <el-icon><Unlock /></el-icon> 免费试看
                    </span>
                    <span v-else-if="selectedEpisode" class="vip-text">
                      <el-icon><Lock /></el-icon> VIP TRC-20
                    </span>
                    <span v-else class="text-dim">-</span>
                  </div>
                </div>

                <div class="spec-card full-span">
                  <div class="spec-label">上游 CDN 域名节点</div>
                  <div class="spec-val mono host-text" :title="currentActiveProbe?.host || '-'">
                    {{ currentActiveProbe?.host || '-' }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 整季一键巡检控制栏 -->
        <div class="season-audit-bar">
          <div class="audit-left">
            <el-button
              v-if="!batchProbing"
              type="primary"
              :icon="VideoPlay"
              @click="startBatchProbe"
            >
              一键并发巡检全季 ({{ episodes.length }} 集)
            </el-button>
            <el-button
              v-else
              type="danger"
              @click="stopBatchProbe"
            >
              终止巡检 ({{ batchCurrent }} / {{ batchTotal }})
            </el-button>

            <!-- 统计标签 -->
            <div class="audit-stats">
              <span class="stat-item success">
                <el-icon><Check /></el-icon> 正常: <b>{{ totalSuccessCount }}</b>
              </span>
              <span class="stat-item danger">
                <el-icon><CircleClose /></el-icon> 断流异常: <b>{{ totalFailedCount }}</b>
              </span>
            </div>
          </div>

          <!-- 视图切换与过滤器 -->
          <div class="audit-right">
            <el-radio-group v-model="episodeViewMode" size="small">
              <el-radio-button value="matrix">
                <el-icon><Grid /></el-icon> 热力网格
              </el-radio-button>
              <el-radio-button value="table">
                <el-icon><List /></el-icon> 列表模式
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- 巡检实时进度条 -->
        <div v-if="batchProbing" class="batch-progress-wrapper">
          <el-progress
            :percentage="batchProgressPercent"
            :stroke-width="8"
            status="success"
            :striped="true"
            :striped-flow="true"
          />
        </div>

        <!-- 热力网格视图 (Episode Heatmap Matrix) -->
        <div v-if="episodeViewMode === 'matrix'" class="heatmap-section">
          <div class="heatmap-filter-bar">
            <span class="filter-label">状态筛选：</span>
            <el-radio-group v-model="heatmapFilter" size="small">
              <el-radio-button value="all">全部 ({{ episodes.length }})</el-radio-button>
              <el-radio-button value="error">断流失效 ({{ totalFailedCount }})</el-radio-button>
              <el-radio-button value="vip">VIP 锁定 ({{ Math.max(0, episodes.length - freeEpisodes) }})</el-radio-button>
              <el-radio-button value="untested">未检测</el-radio-button>
            </el-radio-group>
          </div>

          <div class="heatmap-grid">
            <el-tooltip
              v-for="ep in displayedEpisodes"
              :key="ep.episode"
              placement="top"
              :show-after="150"
            >
              <template #content>
                <div class="tooltip-box">
                  <div class="tt-header">第 {{ ep.episode }} 集 · {{ ep.title || '无标题' }}</div>
                  <div class="tt-row">门禁: {{ ep.episode <= freeEpisodes ? '免费试看' : 'VIP 锁定' }}</div>
                  <div class="tt-row">
                    探针状态:
                    <span v-if="probeMap[ep.episode]?.status === 'success'" style="color: #67c23a;">正常可用</span>
                    <span v-else-if="probeMap[ep.episode]?.status === 'error'" style="color: #f56c6c;">断流异常</span>
                    <span v-else-if="probeMap[ep.episode]?.status === 'probing'" style="color: #409eff;">探测中...</span>
                    <span v-else style="color: #94a3b8;">未探测</span>
                  </div>
                  <div v-if="probeMap[ep.episode]?.duration" class="tt-row mono">
                    时长: {{ formatDuration(probeMap[ep.episode]?.duration) }}
                  </div>
                  <div v-if="probeMap[ep.episode]?.ttfbMs" class="tt-row mono">
                    TTFB: {{ probeMap[ep.episode]?.ttfbMs }} ms
                  </div>
                </div>
              </template>
              <div
                class="heatmap-tile"
                :class="{
                  'is-active': selectedEpisode?.episode === ep.episode,
                  'is-success': probeMap[ep.episode]?.status === 'success',
                  'is-error': probeMap[ep.episode]?.status === 'error',
                  'is-probing': probeMap[ep.episode]?.status === 'probing',
                  'is-vip': ep.episode > freeEpisodes,
                }"
                @click="probeSingleEpisode(ep)"
              >
                <span class="tile-number mono">{{ ep.episode }}</span>
                <el-icon v-if="ep.episode > freeEpisodes" class="tile-lock-icon"><Lock /></el-icon>
              </div>
            </el-tooltip>
          </div>
        </div>

        <!-- 传统数据列表视图 -->
        <div v-else class="episodes-table-section">
          <div class="episodes-toolbar">
            <el-input
              v-model="episodeSearch"
              placeholder="搜索集数 / 章节ID / 标题..."
              :prefix-icon="Search"
              size="small"
              clearable
              class="episode-search-input"
            />
          </div>

          <el-table :data="displayedEpisodes" border size="small" class="episodes-table">
            <el-table-column label="集数" prop="episode" width="90" align="center">
              <template #default="{ row }">
                <el-tag
                  v-if="row.episode <= freeEpisodes"
                  type="success"
                  size="small"
                  effect="light"
                >
                  EP {{ row.episode }}
                </el-tag>
                <el-tag
                  v-else
                  type="warning"
                  size="small"
                  effect="dark"
                >
                  <el-icon><Lock /></el-icon> EP {{ row.episode }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="章节 ID" prop="chapterId" width="130">
              <template #default="{ row }">
                <span class="mono chapter-text">{{ row.chapterId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分集标题" prop="title" min-width="160">
              <template #default="{ row }">
                <span>{{ row.title || `第 ${row.episode} 集` }}</span>
              </template>
            </el-table-column>
            <el-table-column label="探针状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag v-if="probeMap[row.episode]?.status === 'success'" type="success" size="small">
                  正常可用
                </el-tag>
                <el-tag v-else-if="probeMap[row.episode]?.status === 'error'" type="danger" size="small">
                  断流异常
                </el-tag>
                <el-tag v-else-if="probeMap[row.episode]?.status === 'probing'" type="primary" size="small">
                  探测中...
                </el-tag>
                <span v-else class="text-dim">未探测</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  @click="probeSingleEpisode(row)"
                >
                  监看试看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-drawer>

    <!-- 错误详情弹窗 -->
    <el-dialog v-model="errorDetailVisible" :title="currentErrorTitle" width="560px">
      <div class="error-dialog-body">
        <el-alert
          title="错误上下文与系统排查信息"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: 12px;"
        />
        <div class="code-box mono">{{ currentErrorText }}</div>
      </div>
      <template #footer>
        <el-button @click="errorDetailVisible = false">关闭</el-button>
        <el-button type="primary" :icon="CopyDocument" @click="copyText(currentErrorText, '错误日志')">
          一键复制
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.title-with-badge {
  display: flex;
  align-items: center;
  gap: 12px;
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  border-radius: 9999px;
  padding: 0 10px;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background-color: #67c23a;
  border-radius: 50%;
  box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.7);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.7);
  }
  70% {
    transform: scale(1);
    box-shadow: 0 0 0 6px rgba(103, 194, 58, 0);
  }
  100% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(103, 194, 58, 0);
  }
}

.metric-card {
  padding: 20px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  transition: all 0.2s ease-in-out;
  display: flex;
  flex-direction: column;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px -4px rgba(0, 0, 0, 0.05);
}

.metric-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-label {
  font-size: 14px;
  color: #6b7280;
  font-weight: 500;
}

.metric-icon {
  font-size: 20px;
  color: #9ca3af;
}

.metric-value {
  margin: 12px 0 6px;
  font-size: 32px;
  font-weight: 700;
  color: #111827;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.metric-footer {
  font-size: 12px;
  color: #9ca3af;
}

.metric-primary {
  border-left: 4px solid #409eff;
}
.metric-success {
  border-left: 4px solid #67c23a;
}
.metric-warning {
  border-left: 4px solid #e6a23c;
}

.workbench-card {
  border-radius: 10px;
  border: 1px solid #e5e7eb;
}

.dramas-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.dramas-filter {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}

.search-input {
  max-width: 340px;
}

.count-badge {
  font-size: 13px;
  color: #6b7280;
}

.poster-grid-container {
  min-height: 300px;
}

.poster-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 18px;
}

.poster-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  transition: all 0.2s ease-in-out;
}

.poster-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.08);
  border-color: #c0c4cc;
}

.poster-cover-wrapper {
  position: relative;
  width: 100%;
  aspect-ratio: 9 / 13;
  background: #f3f4f6;
  overflow: hidden;
}

.poster-image {
  width: 100%;
  height: 100%;
  transition: transform 0.3s ease;
}

.poster-card:hover .poster-image {
  transform: scale(1.04);
}

.image-fallback,
.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #9ca3af;
  font-size: 12px;
}

.episode-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.75);
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: 9999px;
  backdrop-filter: blur(4px);
}

.poster-content {
  padding: 12px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.poster-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
  height: 2.8em;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.poster-id {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.poster-desc {
  font-size: 12px;
  color: #6b7280;
  margin-top: 6px;
  line-height: 1.4;
  height: 2.8em;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.poster-actions {
  margin-top: auto;
  padding-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.table-thumb {
  width: 48px;
  height: 64px;
  border-radius: 4px;
  display: block;
}

.table-thumb-fallback {
  width: 100%;
  height: 100%;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
}

.table-title-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.table-title-text {
  font-weight: 600;
  color: #1f2937;
}

.table-subtitle {
  font-size: 12px;
  color: #9ca3af;
}

.copyable-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sub-section {
  background: #ffffff;
}

.section-heading {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 14px 0;
}

.shelf-pill {
  padding: 3px 8px;
  background: #f3f4f6;
  border-radius: 4px;
  font-size: 13px;
  color: #374151;
}

.error-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 220px;
}

.text-muted {
  color: #9ca3af;
}

/* 抽屉样式 */
.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.book-header-card {
  display: flex;
  gap: 16px;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.drawer-cover {
  width: 80px;
  height: 112px;
  border-radius: 6px;
  flex-shrink: 0;
  background: #e2e8f0;
}

.drawer-cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 24px;
}

.book-header-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.book-main-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.book-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
  flex-wrap: wrap;
}

.book-desc-box {
  margin-top: 4px;
  font-size: 12px;
  color: #475569;
  line-height: 1.4;
  background: #ffffff;
  padding: 6px 10px;
  border-radius: 4px;
  border: 1px solid #f1f5f9;
  max-height: 48px;
  overflow-y: auto;
}

/* Cinematic 暗色审片监看台 */
.cinematic-monitor {
  background: #090d16;
  border: 1px solid #1e293b;
  border-radius: 10px;
  padding: 14px;
  color: #f8fafc;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.4);
}

.monitor-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #1e293b;
  padding-bottom: 10px;
  margin-bottom: 12px;
}

.monitor-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pulse-indicator {
  width: 8px;
  height: 8px;
  background-color: #38bdf8;
  border-radius: 50%;
  box-shadow: 0 0 8px #38bdf8;
}

.monitor-label {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}

.monitor-ep-text {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
}

.dark-btn {
  color: #94a3b8;
}
.dark-btn:hover {
  color: #38bdf8;
}

.monitor-body {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 16px;
}

@media (max-width: 768px) {
  .monitor-body {
    grid-template-columns: 1fr;
  }
}

.video-stage {
  background: #000000;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  aspect-ratio: 9 / 13;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #1e293b;
}

.studio-video-element {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #000000;
}

.video-playback-hint {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 56px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(24, 24, 32, 0.92);
  color: #f5c451;
  font-size: 12px;
  line-height: 1.5;
  flex-wrap: wrap;
}

.video-playback-hint a {
  color: #7ab8ff;
  text-decoration: underline;
}

.video-playback-state {
  width: 100%;
  color: #94a3b8;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  word-break: break-all;
}

.video-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 8px;
  padding: 16px;
  font-size: 13px;
  color: #94a3b8;
}

.probing-glow {
  color: #38bdf8;
  font-size: 13px;
}

.error-glow {
  color: #f87171;
}

.error-subtext {
  font-size: 11px;
  color: #ef4444;
  margin-top: 4px;
}

.idle-glow {
  color: #64748b;
}

/* 参数胶囊控制台 */
.specs-capsule-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.specs-title {
  font-size: 13px;
  font-weight: 600;
  color: #cbd5e1;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.specs-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.spec-card {
  background: #111827;
  border: 1px solid #1f2937;
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.spec-card.full-span {
  grid-column: 1 / -1;
}

.spec-label {
  font-size: 11px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 4px;
}

.spec-val {
  font-size: 13px;
  color: #f3f4f6;
  font-weight: 500;
}

.latency-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.lat-fast {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
}
.lat-medium {
  background: rgba(234, 179, 8, 0.2);
  color: #facc15;
}
.lat-slow {
  background: rgba(239, 68, 68, 0.2);
  color: #f87171;
}

.free-text {
  color: #4ade80;
  font-weight: 600;
  font-size: 12px;
}

.vip-text {
  color: #facc15;
  font-weight: 600;
  font-size: 12px;
}

.text-dim {
  color: #64748b;
}

.host-text {
  word-break: break-all;
  color: #60a5fa;
  font-size: 12px;
}

/* 巡检控制栏 */
.season-audit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 14px;
  flex-wrap: wrap;
  gap: 12px;
}

.audit-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.audit-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.stat-item.success {
  color: #16a34a;
  display: flex;
  align-items: center;
  gap: 4px;
}

.stat-item.danger {
  color: #dc2626;
  display: flex;
  align-items: center;
  gap: 4px;
}

.batch-progress-wrapper {
  margin: -6px 0 6px;
}

/* 热力图网格 */
.heatmap-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.heatmap-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 12px;
  color: #64748b;
}

.heatmap-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(42px, 1fr));
  gap: 6px;
  max-height: 280px;
  overflow-y: auto;
  padding: 4px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.heatmap-tile {
  height: 42px;
  border-radius: 6px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  transition: all 0.15s ease-in-out;
  user-select: none;
}

.heatmap-tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  border-color: #94a3b8;
}

.heatmap-tile.is-active {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.3);
  font-weight: 700;
}

.heatmap-tile.is-success {
  background: #22c55e;
  border-color: #16a34a;
  color: #ffffff;
}

.heatmap-tile.is-error {
  background: #ef4444;
  border-color: #dc2626;
  color: #ffffff;
}

.heatmap-tile.is-probing {
  background: #38bdf8;
  border-color: #0284c7;
  color: #ffffff;
  animation: pulse 1.5s infinite;
}

.tile-number {
  font-size: 12px;
  font-weight: 600;
}

.tile-lock-icon {
  position: absolute;
  top: 2px;
  right: 2px;
  font-size: 9px;
  opacity: 0.8;
}

.tooltip-box {
  font-size: 12px;
  line-height: 1.5;
}

.tt-header {
  font-weight: 600;
  margin-bottom: 2px;
}

.tt-row {
  color: #e2e8f0;
}

/* 列表模式 */
.episodes-table-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.episodes-toolbar {
  display: flex;
  justify-content: flex-end;
}

.episode-search-input {
  width: 240px;
}

.chapter-text {
  font-size: 11px;
  color: #64748b;
}

.code-box {
  background: #1e293b;
  color: #f8fafc;
  padding: 14px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
</style>
