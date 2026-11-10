package com.twobgreen.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The schema of P7 as Flyway builds it on an empty database, checked against the rules of the inventory
 * ({@code docs/data/user-data.md}): one group of tables per module, no foreign key between modules, every row
 * reachable from a user id.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserDataSchemaTests {

	private static final Set<String> MODULES = Set.of("advisory", "automation", "regional", "delivery");

	/** One table per row of the inventory. */
	private static final Set<String> INVENTORY = Set.of(
			"automation_alert_rule", // 1
			"delivery_notification", // 2
			"advisory_usage", // 3
			"advisory_usage_log_entry", // 4
			"advisory_region_preference", // 5
			"automation_api_key", // 6
			"automation_api_key_daily_counter", // 7
			"automation_webhook", // 8
			"delivery_webhook_delivery", // 9
			"regional_target", // 10
			"regional_target_progress", // 11
			"automation_rule_state"); // 12

	@Autowired
	private JdbcClient jdbc;

	@Test
	void migrationsBuildExactlyTheTablesOfTheInventory() {
		assertThat(tables()).containsExactlyInAnyOrderElementsOf(INVENTORY);
	}

	@Test
	void everyMigrationWasApplied() {
		List<String> versions = jdbc.sql("SELECT version FROM platform.flyway_schema_history WHERE success ORDER BY 1")
				.query(String.class).list();

		assertThat(versions).containsExactly("1", "2", "3", "4");
	}

	@Test
	void everyTableBelongsToOneModule() {
		assertThat(tables()).allSatisfy(table -> assertThat(MODULES).contains(moduleOf(table)));
	}

	@Test
	void noForeignKeyLeavesItsModule() {
		List<Map<String, Object>> foreignKeys = jdbc.sql("""
				SELECT source.relname AS source, target.relname AS target
				FROM pg_constraint c
				JOIN pg_class source ON source.oid = c.conrelid
				JOIN pg_class target ON target.oid = c.confrelid
				JOIN pg_namespace n ON n.oid = source.relnamespace
				WHERE c.contype = 'f' AND n.nspname = 'platform'
				""").query().listOfRows();

		// The two links inside a module: a counter follows its key, a progress row follows its target
		assertThat(foreignKeys).hasSize(2);
		assertThat(foreignKeys).allSatisfy(fk -> assertThat(moduleOf((String) fk.get("source")))
				.isEqualTo(moduleOf((String) fk.get("target"))));
	}

	@Test
	void everyTableIsReachableFromAUserId() {
		Set<String> withUserId = new TreeSet<>(jdbc.sql("""
				SELECT table_name FROM information_schema.columns
				WHERE table_schema = 'platform' AND column_name = 'user_id' AND is_nullable = 'NO'
				""").query(String.class).list());
		Set<String> without = new TreeSet<>(tables());
		without.removeAll(withUserId);

		// These two are deleted with their parent (ON DELETE CASCADE), which has the user id
		assertThat(without).containsExactly("automation_api_key_daily_counter", "regional_target_progress");
		assertThat(jdbc.sql("""
				SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
				WHERE c.contype = 'f' AND n.nspname = 'platform' AND c.confdeltype = 'c'
				""").query(Long.class).single()).isEqualTo(2);
	}

	@Test
	void everyEntityMapsATableOfItsOwnModule() {
		Map<String, String> moduleByTable = new TreeMap<>();
		var scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Table.class));
		scanner.findCandidateComponents("com.twobgreen.platform").forEach(candidate -> {
			Class<?> entity = load(candidate.getBeanClassName());
			String module = entity.getPackageName().substring("com.twobgreen.platform.".length()).split("\\.")[0];
			moduleByTable.put(entity.getAnnotation(Table.class).value(), module);
		});

		assertThat(moduleByTable).allSatisfy((table, module) -> assertThat(moduleOf(table)).isEqualTo(module));
		// The counter has no entity: it is only written by one atomic statement of ApiKeyRepository
		Set<String> expected = new TreeSet<>(INVENTORY);
		expected.remove("automation_api_key_daily_counter");
		assertThat(moduleByTable.keySet()).containsExactlyInAnyOrderElementsOf(expected);
	}

	/** Tables of the schema, without the two that are not user data: Flyway's history and Spring Modulith's event log. */
	private List<String> tables() {
		return jdbc.sql("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'platform' AND table_type = 'BASE TABLE'
				AND table_name NOT IN ('flyway_schema_history', 'event_publication')
				""").query(String.class).list();
	}

	private static String moduleOf(String table) {
		return table.substring(0, table.indexOf('_'));
	}

	private static Class<?> load(String name) {
		try {
			return Class.forName(name);
		}
		catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}
}
