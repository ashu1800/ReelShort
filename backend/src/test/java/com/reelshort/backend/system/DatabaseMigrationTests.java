package com.reelshort.backend.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:flyway-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true",
		"reelshort.rate-limit.enabled=false"
})
class DatabaseMigrationTests {

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayAppliesInitialSchemaBeforeJpaValidation() {
		var migrations = flyway.info().applied();

		assertThat(migrations).isNotEmpty();
		assertThat(migrations[0].getVersion().getVersion()).isEqualTo("1");
		assertThat(migrations[0].getState().isApplied()).isTrue();
		assertThat(existingTables()).contains(
				"users",
				"access_tokens",
				"admin_users",
				"roles",
				"permissions",
				"content_book_cache",
				"watch_records",
				"point_accounts",
				"recharge_orders",
				"payment_events",
				"content_refresh_runs",
				"system_configs"
		);
	}

	@Test
	void migrationCreatesUniqueUsernameConstraint() {
		var now = Timestamp.from(OffsetDateTime.now().toInstant());
		var id1 = UUID.randomUUID();
		var id2 = UUID.randomUUID();

		jdbcTemplate.update("""
				insert into users (id, username, password_hash, status, created_at)
				values (?, ?, ?, ?, ?)
				""", id1, "duplicate-user", "hash", "ACTIVE", now);

		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into users (id, username, password_hash, status, created_at)
				values (?, ?, ?, ?, ?)
				""", id2, "duplicate-user", "hash", "ACTIVE", now))
			.hasMessageContaining("duplicate-user");
	}

	@Test
	void contentBookCacheSupportsTraditionalChineseLocaleKeysAfterMigration() {
		var now = Timestamp.from(OffsetDateTime.now().toInstant());

		jdbcTemplate.update("""
				insert into content_book_cache (id, book_id, locale, title, filtered_title, cover_url, description, chapter_count, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				"12345678-1234-1234-1234-1234567890ab",
				"670f2d8f4f3f2cb1c8ab1234",
				"TRADITIONAL_CHINESE",
				"億萬戀人",
				"billionaire-lover",
				"https://example.com/cover.jpg",
				"desc",
				88,
				now);

		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from content_book_cache
				where book_id = ? and locale = ?
				""", Integer.class, "670f2d8f4f3f2cb1c8ab1234", "TRADITIONAL_CHINESE");

		assertThat(count).isEqualTo(1);
	}

	@Test
	void contentRefreshRunsStoresOperationalRefreshHistory() {
		var now = Timestamp.from(OffsetDateTime.now().toInstant());
		var id = UUID.randomUUID();

		jdbcTemplate.update("""
				insert into content_refresh_runs (
					id, trigger_source, shelf_type, locale, status,
					started_at, finished_at, duration_millis, item_count, error_message
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				id,
				"ADMIN",
				"RECOMMEND",
				"ENGLISH",
				"SUCCESS",
				now,
				now,
				123L,
				500,
				null);

		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from content_refresh_runs
				where trigger_source = ? and shelf_type = ? and locale = ? and status = ?
				""", Integer.class, "ADMIN", "RECOMMEND", "ENGLISH", "SUCCESS");

		assertThat(count).isEqualTo(1);
	}

	private List<String> existingTables() {
		return jdbcTemplate.queryForList("""
				select table_name
				from information_schema.tables
				where lower(table_schema) = 'public'
				""", String.class);
	}
}
