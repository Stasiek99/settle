package dev.settle.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationOnExistingDataTest {

    private static final String SCHEMA_WITH_DATA_FROM = "3";

    private static PostgreSQLContainer postgres;

    private JdbcTemplate jdbc;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() {
        postgres.stop();
    }

    @BeforeEach
    void startFromAnEmptySchema() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        flyway(null).clean();
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }

    private void insertDataAsTheFirstVersionsLeftIt() {
        jdbc.update("insert into users (id, email, display_name) values (?::uuid, 'ann@example.com', 'Ann'), "
                + "(?::uuid, 'bob@example.com', 'Bob')", ANN, BOB);
        jdbc.update("insert into groups (id, name, currency, created_by, rotation_cursor) "
                + "values (?::uuid, 'Trip', 'EUR', ?::uuid, 7)", TRIP, ANN);
        jdbc.update("insert into memberships (group_id, user_id, role) values (?::uuid, ?::uuid, 'OWNER'), "
                + "(?::uuid, ?::uuid, 'MEMBER')", TRIP, ANN, TRIP, BOB);
        jdbc.update("insert into expenses (id, group_id, paid_by, description, amount_minor, currency, split_type) "
                + "values (?::uuid, ?::uuid, ?::uuid, 'Dinner', 10001, 'EUR', 'EQUAL')", DINNER, TRIP, ANN);
        jdbc.update("insert into expense_splits (expense_id, user_id, amount_minor) values (?::uuid, ?::uuid, 5001), "
                + "(?::uuid, ?::uuid, 5000)", DINNER, ANN, DINNER, BOB);
        jdbc.update("insert into settlements (group_id, paid_by, paid_to, amount_minor, currency) "
                + "values (?::uuid, ?::uuid, ?::uuid, 5000, 'EUR')", TRIP, BOB, ANN);
    }

    private static final String ANN = "00000000-0000-0000-0000-00000000000a";
    private static final String BOB = "00000000-0000-0000-0000-00000000000b";
    private static final String TRIP = "00000000-0000-0000-0000-0000000000c1";
    private static final String DINNER = "00000000-0000-0000-0000-0000000000d1";

    @Test
    void migratingFromAnOlderSchemaKeepsEveryRow() {
        flyway(SCHEMA_WITH_DATA_FROM).migrate();
        insertDataAsTheFirstVersionsLeftIt();

        flyway(null).migrate();

        assertThat(count("users")).isEqualTo(2);
        assertThat(count("groups")).isEqualTo(1);
        assertThat(count("memberships")).isEqualTo(2);
        assertThat(count("expenses")).isEqualTo(1);
        assertThat(count("expense_splits")).isEqualTo(2);
        assertThat(count("settlements")).isEqualTo(1);
    }

    @Test
    void columnsAddedLaterGetUsableDefaultsOnOldRows() {
        flyway(SCHEMA_WITH_DATA_FROM).migrate();
        insertDataAsTheFirstVersionsLeftIt();

        flyway(null).migrate();

        assertThat(jdbc.queryForObject("select version from groups", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select rotation_cursor from groups", Long.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select version from expenses", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select split_cursor from expenses", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select updated_at is not null from expenses", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from users where password_hash is null", Long.class))
                .isEqualTo(2);
    }

    @Test
    void amountsAndSplitsAreUntouched() {
        flyway(SCHEMA_WITH_DATA_FROM).migrate();
        insertDataAsTheFirstVersionsLeftIt();

        flyway(null).migrate();

        assertThat(jdbc.queryForObject("select amount_minor from expenses", Long.class)).isEqualTo(10001);
        assertThat(jdbc.queryForObject("select sum(amount_minor) from expense_splits", Long.class))
                .isEqualTo(10001);
    }

    @Test
    void migratingAnEmptyDatabaseToTheLatestVersionLeavesNothingPending() {
        flyway(null).migrate();

        assertThat(flyway(null).info().pending()).isEmpty();
        flyway(null).validate();
    }

    @Test
    void runningMigrateAgainChangesNothing() {
        flyway(null).migrate();

        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
    }

    private long count(String table) {
        Long result = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return result == null ? 0 : result;
    }
}
