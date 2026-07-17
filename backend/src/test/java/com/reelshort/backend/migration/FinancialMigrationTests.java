package com.reelshort.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FinancialMigrationTests {

	@Test
	void historicalMigrationsNeverDeleteOrRelabelFinancialData() throws IOException {
		String v13 = migration("db/migration/V13__refactor_auth_vip.sql");
		String v18 = migration("db/migration/V18__fair_mode_fractional_field.sql");
		String v19 = migration("db/migration/V19__wallet_network_erc20.sql");

		assertThat(v13)
				.doesNotContain("delete from")
				.doesNotContain("drop table")
				.doesNotContain("drop column");
		assertThat(v18)
				.doesNotContain("update point_accounts")
				.doesNotContain("update point_transactions")
				.doesNotContain("update withdrawal_requests")
				.doesNotContain("update watch_episode_reward_claims");
		assertThat(v19).doesNotContain("update user_wallets");
	}

	@Test
	void upgradingFromV12PreservesExistingFinancialAndAuthenticationData() {
		DataSource dataSource = dataSource("financial-migration-preserves-data");
		Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.target(MigrationVersion.fromVersion("12"))
				.load()
				.migrate();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID userId = UUID.randomUUID();
		Timestamp now = Timestamp.from(OffsetDateTime.now().toInstant());

		jdbc.update("""
				insert into users (id, username, password_hash, status, created_at, phone_country_code, phone_number, phone_e164)
				values (?, ?, ?, ?, ?, ?, ?, ?)
				""", userId, "legacy-financial-user", "hash", "ACTIVE", now, "+86", "13800000000", "+8613800000000");
		jdbc.update("""
				insert into point_accounts (id, user_id, balance, frozen_points, updated_at)
				values (?, ?, ?, ?, ?)
				""", UUID.randomUUID(), userId, 123, 23, now);
		jdbc.update("""
				insert into point_transactions (id, user_id, amount, balance_after, source, reason, created_at)
				values (?, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), userId, 17, 123, "ADMIN_ADJUSTMENT", "legacy", now);
		jdbc.update("""
				insert into withdrawal_requests (
				    id, user_id, point_amount, usdt_amount, usdt_per_point, network, wallet_address, status, created_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), userId, 23, "2.300000", "0.10000000", "TRC20", "TLegacyWallet", "PENDING", now);
		jdbc.update("""
				insert into user_wallets (id, user_id, network, wallet_address, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), userId, "TRC20", "TLegacyWallet", now, now);
		jdbc.update("""
				insert into sms_verification_codes (
				    id, purpose, phone_country_code, phone_number, phone_e164, code_hash, expires_at, created_at
				) values (?, ?, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), "REGISTER", "+86", "13800000000", "+8613800000000", "legacy-hash", now, now);

		Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.load()
				.migrate();

		assertThat(count(jdbc, "users")).isEqualTo(1);
		assertThat(count(jdbc, "point_accounts")).isEqualTo(1);
		assertThat(count(jdbc, "point_transactions")).isEqualTo(1);
		assertThat(count(jdbc, "withdrawal_requests")).isEqualTo(1);
		assertThat(count(jdbc, "user_wallets")).isEqualTo(1);
		assertThat(count(jdbc, "sms_verification_codes")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select balance from point_accounts where user_id = ?", Integer.class, userId))
				.isEqualTo(123);
		assertThat(jdbc.queryForObject("select amount from point_transactions where user_id = ?", Integer.class, userId))
				.isEqualTo(17);
		assertThat(jdbc.queryForObject("select network from user_wallets where user_id = ?", String.class, userId))
				.isEqualTo("TRC20");
	}

	@Test
	void financialSafetyMigrationDeclaresPayoutAndCollectionInvariants() throws IOException {
		ClassPathResource resource = new ClassPathResource("db/migration/V20__financial_safety.sql");
		assertThat(resource.exists()).isTrue();
		String sql = migration("db/migration/V20__financial_safety.sql");

		assertThat(sql).contains(
				"create table withdrawal_payout_attempts",
				"withdrawal_request_id",
				"signed_raw_transaction text",
				"active_slot",
				"unique (withdrawal_request_id, attempt_number)",
				"unique (withdrawal_request_id, active_slot)",
				"create table hot_wallet_nonces",
				"unique (network, wallet_address, chain_id)",
				"add column if not exists receiving_network",
				"add column if not exists receiving_wallet_address",
				"add column if not exists token_contract_address",
				"add column if not exists base_usdt_amount",
				"add column if not exists payable_usdt_amount",
				"add column if not exists pending_slot",
				"unique (user_id, pending_slot)",
				"unique (payable_usdt_amount, pending_slot)",
				"uk_vip_orders_tx_hash",
				"add column if not exists idempotency_key",
				"uk_point_transactions_idempotency_key",
				"check (balance >= 0)",
				"check (frozen_points >= 0)",
				"check (frozen_points <= balance)",
				"check (fractional_part between 0 and 9)",
				"'order_write'");
	}

	private DataSource dataSource(String databaseName) {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:mem:" + databaseName
				+ ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");
		return dataSource;
	}

	private int count(JdbcTemplate jdbc, String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	private String migration(String path) throws IOException {
		ClassPathResource resource = new ClassPathResource(path);
		assertThat(resource.exists()).as("migration %s exists", path).isTrue();
		try (var input = resource.getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
		}
	}
}
