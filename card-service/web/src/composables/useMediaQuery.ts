import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/** 媒体查询响应式封装（用于移动端侧边栏折叠）。 */
export function useMediaQuery(query: string): Ref<boolean> {
  const matches = ref(false)
  let mediaQueryList: MediaQueryList | null = null

  const update = (event: MediaQueryList | MediaQueryListEvent): void => {
    matches.value = event.matches
  }

  onMounted(() => {
    mediaQueryList = window.matchMedia(query)
    update(mediaQueryList)
    mediaQueryList.addEventListener('change', update)
  })

  onBeforeUnmount(() => {
    mediaQueryList?.removeEventListener('change', update)
    mediaQueryList = null
  })

  return matches
}

/** 小于 768px 视为移动端（侧边栏折叠为抽屉）。 */
export function useIsMobile(): Ref<boolean> {
  return useMediaQuery('(max-width: 767px)')
}
