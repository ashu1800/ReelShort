import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const viewSource = readFileSync(new URL('../src/views/ContentCacheView.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../src/services/adminApi.ts', import.meta.url), 'utf8')
const appSource = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')

test('content management view removes redundant locale selector and refresh-locales', () => {
  assert.doesNotMatch(viewSource, /refreshContentShelfLocales/)
  assert.doesNotMatch(viewSource, /localeOptions/)
  assert.match(viewSource, /视频管理/)
})

test('content management view includes episode heatmap matrix and batch audit controls', () => {
  assert.match(viewSource, /heatmap-grid/)
  assert.match(viewSource, /startBatchProbe/)
  assert.match(viewSource, /cinematic-monitor/)
  assert.match(viewSource, /specs-capsule-panel/)
})

test('video library grid uses server-side pagination', () => {
  // 库里可能有上万部：两个视图都只吃后端返回的当前页，总数由后端给。
  assert.match(viewSource, /v-for="book in books"/)
  assert.match(viewSource, /:data="books"/)
  assert.match(viewSource, /<el-pagination/)
  assert.match(viewSource, /:total="libraryTotal"/)
  assert.match(viewSource, /@current-change="handlePageChange"/)
})

test('monitoring tab renders the library status with formatted timestamps', () => {
  assert.match(viewSource, /libraryStats/)
  assert.match(viewSource, /formatDateTime\(status\.value\.lastCrawledAt\)/)
  assert.match(viewSource, /抓取一轮新书/)
})

test('adminApi exposes library books, episodes, and video probe endpoints', () => {
  assert.match(apiSource, /export async function fetchContentLibraryBooks/)
  assert.match(apiSource, /export async function fetchContentLibraryStatus/)
  assert.match(apiSource, /export async function crawlContentLibrary/)
  assert.match(apiSource, /export async function fetchBookEpisodes/)
  assert.match(apiSource, /export async function probeEpisodeVideo/)
})

test('app navigation exposes video content management entry', () => {
  assert.match(appSource, /视频管理/)
  assert.match(appSource, /index="\/content-cache"/)
})
