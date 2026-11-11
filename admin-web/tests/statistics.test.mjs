import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const viewSource = readFileSync(new URL('../src/views/StatisticsView.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../src/services/adminApi.ts', import.meta.url), 'utf8')
const appSource = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const routerSource = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')

test('statistics page is reachable from the admin navigation', () => {
  assert.match(appSource, /index="\/statistics"/)
  assert.match(appSource, /数据统计/)
  assert.match(routerSource, /path: '\/statistics'/)
  assert.match(routerSource, /component: StatisticsView/)
})

test('statistics page renders app metrics, task metrics and total points', () => {
  assert.match(viewSource, /App 内统计/)
  assert.match(viewSource, /视频观看任务（刷积分）/)
  assert.match(viewSource, /用户播放量/)
  assert.match(viewSource, /点赞数量/)
  assert.match(viewSource, /收藏数量/)
  assert.match(viewSource, /评论数量/)
  assert.match(viewSource, /任务播放量/)
  assert.match(viewSource, /积分总获取数量/)
  assert.match(viewSource, /积分平均获取数量/)
  assert.match(viewSource, /全站用户总积分/)
})

test('statistics page reloads when the time range changes', () => {
  assert.match(viewSource, /fetchAdminStatistics/)
  for (const label of ['今日', '近 7 天', '近 30 天', '累计']) {
    assert.match(viewSource, new RegExp(label))
  }
  assert.match(viewSource, /watch\(range, loadStatistics\)/)
})

test('adminApi exposes the statistics endpoint and response contract', () => {
  assert.match(apiSource, /export async function fetchAdminStatistics/)
  assert.match(apiSource, /http\.get<ApiResponse<AdminStatistics>>\('\/statistics'/)
  assert.match(apiSource, /averagePoints: string/)
  assert.match(apiSource, /totalPoints: string/)
})
