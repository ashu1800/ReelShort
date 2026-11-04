import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const viewSource = readFileSync(new URL('../src/views/ContentCacheView.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../src/services/adminApi.ts', import.meta.url), 'utf8')
const appSource = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')

test('content management view removes redundant locale selector and refresh-locales', () => {
  assert.doesNotMatch(viewSource, /refreshContentShelfLocales/)
  assert.doesNotMatch(viewSource, /localeOptions/)
  assert.match(viewSource, /视频内容管理/)
})

test('content management view includes episode heatmap matrix and batch audit controls', () => {
  assert.match(viewSource, /heatmap-grid/)
  assert.match(viewSource, /startBatchProbe/)
  assert.match(viewSource, /cinematic-monitor/)
  assert.match(viewSource, /specs-capsule-panel/)
})

test('adminApi exposes shelf books, episodes, and video probe endpoints', () => {
  assert.match(apiSource, /export async function fetchShelfBooks/)
  assert.match(apiSource, /export async function fetchBookEpisodes/)
  assert.match(apiSource, /export async function probeEpisodeVideo/)
})

test('app navigation exposes video content management entry', () => {
  assert.match(appSource, /视频内容管理/)
  assert.match(appSource, /index="\/content-cache"/)
})
