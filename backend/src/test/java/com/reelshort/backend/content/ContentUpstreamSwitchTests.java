package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;

/**
 * 上游切换必须真的把已落库的货架清掉。
 *
 * <p>只用 mock 断言 {@code clearShelves()} 被调用是不够的：调用点在配置变更事件的
 * {@code afterCommit} 回调里，那里原事务已经提交，传播级别用错（REQUIRED）时删除会被
 * EntityManager 静默丢弃——mock 全绿，线上货架一动不动。这里落真行、走真事务、断真结果。
 */
@SpringBootTest
class ContentUpstreamSwitchTests {

	@Autowired
	private SystemConfigService systemConfigService;

	@Autowired
	private ContentShelfCacheRepository contentShelfCacheRepository;

	@MockitoBean
	private ContentProvider contentProvider;

	@BeforeEach
	void stubProvider() {
		// 切换会触发后台重抓；返回空列表即可，这里只关心货架行有没有被删掉。
		when(contentProvider.getShelf(any(ContentShelfType.class), any(ContentLocale.class))).thenReturn(List.of());
	}

	@AfterEach
	void cleanUp() {
		contentShelfCacheRepository.deleteAll();
	}

	@Test
	void switchingUpstreamDeletesPersistedShelves() {
		// recommend 会被后台重抓覆盖，用 drama-dub 才能验证"删除"本身（没有别的写入方）。
		contentShelfCacheRepository.saveAndFlush(
				ContentShelfCache.create(ContentShelfType.DRAMA_DUB, "[]", 0));

		systemConfigService.update(SystemConfigRegistry.CONTENT_UPSTREAM, "REELSHORT");

		assertThat(contentShelfCacheRepository.findByShelfTypeAndLocale(ContentShelfType.DRAMA_DUB,
				ContentLocale.ENGLISH)).isEmpty();
	}

	@Test
	void resavingTheSameUpstreamKeepsShelves() {
		// 先落一个确定的基线值，再把货架行写进去——顺序很重要，切换本身就会清货架。
		systemConfigService.update(SystemConfigRegistry.CONTENT_UPSTREAM, "TWISTYTV");
		contentShelfCacheRepository.saveAndFlush(
				ContentShelfCache.create(ContentShelfType.DRAMA_DUB, "[]", 0));

		// 值没变就不该触发清空 + 全量重抓。
		systemConfigService.update(SystemConfigRegistry.CONTENT_UPSTREAM, "TWISTYTV");

		assertThat(contentShelfCacheRepository.findByShelfTypeAndLocale(ContentShelfType.DRAMA_DUB,
				ContentLocale.ENGLISH)).isPresent();
	}
}
