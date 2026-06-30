package com.reelshort.app.session

import com.reelshort.app.data.BookSummary

/**
 * 首页货架数据的持久化缓存边界。
 *
 * 用于冷启动时秒开显示缓存数据，随后由状态层在后台静默拉取最新数据替换。
 * 读取失败应返回空列表而非抛错（自愈降级），由调用方决定是否走网络加载。
 */
interface HomeShelfStore {
    suspend fun loadHomeShelf(): List<BookSummary>

    suspend fun saveHomeShelf(shelf: List<BookSummary>)

    suspend fun clearHomeShelf()
}
