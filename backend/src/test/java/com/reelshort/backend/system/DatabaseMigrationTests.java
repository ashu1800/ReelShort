package com.reelshort.backend.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
	void contentLocaleMigrationUsesStableIdForExistingLongBookIds() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:mem:flyway-upgrade-long-book-id;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");
		Flyway baselineFlyway = Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.target(MigrationVersion.fromVersion("5"))
				.load();
		baselineFlyway.migrate();
		JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
		String longBookId = "670f2d8f4f3f2cb1c8ab1234567890abcdef";
		var now = Timestamp.from(OffsetDateTime.now().toInstant());

		upgradeJdbc.update("""
				insert into content_book_cache (book_id, title, filtered_title, cover_url, description, chapter_count, updated_at)
				values (?, ?, ?, ?, ?, ?, ?)
				""",
				longBookId,
				"Long Id Drama",
				"long-id-drama",
				"https://example.com/long.jpg",
				"desc",
				66,
				now);
		Flyway upgradeFlyway = Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.load();
		upgradeFlyway.migrate();

		String storedId = upgradeJdbc.queryForObject("""
				select id
				from content_book_cache
				where book_id = ? and locale = ?
				""", String.class, longBookId, "ENGLISH");

		assertThat(storedId).hasSizeLessThanOrEqualTo(36);
		assertThat(storedId).isNotEqualTo(longBookId);
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
