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

test('shelf asset grid paginates instead of rendering the whole shelf', () => {
  // 735 部短剧一次性挂到 DOM 会卡顿；两个视图都必须吃当前页切片，
  // 分页总数基于筛选后的全量结果。
  assert.match(viewSource, /v-for="book in pagedBooks"/)
  assert.match(viewSource, /:data="pagedBooks"/)
  assert.match(viewSource, /<el-pagination/)
  assert.match(viewSource, /:total="filteredBooks\.length"/)
})

test('monitoring tab formats ISO timestamps instead of rendering them raw', () => {
  assert.match(viewSource, /formatDateTime\(row\.refreshedAt\)/)
  assert.match(viewSource, /formatDateTime\(row\.startedAt\)/)
})

test('adminApi exposes shelf books, episodes, and video probe endpoints', () => {
  assert.match(apiSource, /export async function fetchShelfBooks/)
  assert.match(apiSource, /export async function fetchBookEpisodes/)
  assert.match(apiSource, /export async function probeEpisodeVideo/)
})

test('app navigation exposes video content management entry', () => {
  assert.match(appSource, /视频管理/)
  assert.match(appSource, /index="\/content-cache"/)
})
