package wamddu.backend.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import wamddu.backend.global.exception.ApiException;

import javax.sql.DataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(AdminTableUpdateTest.Config.class)
class AdminTableUpdateTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(
                "jdbc:h2:mem:admin_update;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean AdminTableService service(JdbcTemplate jdbc) { return new AdminTableService(jdbc); }
    }
    @Autowired AdminTableService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username VARCHAR(100) NOT NULL UNIQUE, phonenumber VARCHAR(100), password VARCHAR(100))");
        jdbc.update("INSERT INTO users VALUES (1, 'first', NULL, 'hash1'), (2, 'second', '010', 'hash2')");
    }

    private AdminTableService.RowChange change(String id, String column, String before, String after) {
        var original = new HashMap<String, String>(); original.put(column, before);
        var values = new HashMap<String, String>(); values.put(column, after);
        return new AdminTableService.RowChange(id, original, values);
    }

    @Test
    void blocksOrderAndPaymentCreationWithoutBlockingUpdatesOrDeletes() {
        for (String table : List.of("orders", "payments")) {
            jdbc.execute("CREATE TABLE " + table + " (id BIGINT AUTO_INCREMENT PRIMARY KEY, status VARCHAR(30))");
            jdbc.update("INSERT INTO " + table + " (status) VALUES ('PENDING')");
            assertThatThrownBy(() -> service.save(table, List.of(change("1", "status", "PENDING", "PAID")),
                    List.of(Map.of("status", "PENDING")), List.of(), 0, 100)).isInstanceOf(ApiException.class);
            assertThat(jdbc.queryForList("SELECT status FROM " + table, String.class)).containsExactly("PENDING");
            service.save(table, List.of(change("1", "status", "PENDING", "PAID")), List.of(), List.of(), 0, 100);
            var result = service.save(table, List.of(), List.of(),
                    List.of(new AdminTableService.RowDeletion("1", Map.of("id", "1", "status", "PAID"))), 0, 100);
            assertThat(result.totalRows()).isZero();
        }
    }

    @Test
    void persistsChangesAndReturnsTheSavedPage() {
        var page = service.update("users", List.of(change("1", "username", "first", "edited"),
                change("2", "phonenumber", "010", null)), 0, 100);
        assertThat(page.rows().getFirst()).contains("edited");
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=1", String.class)).isEqualTo("edited");
        assertThat(jdbc.queryForObject("SELECT phonenumber FROM users WHERE id=2", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT password FROM users WHERE id=1", String.class)).isEqualTo("hash1");
    }

    @Test
    void staleValueRollsBackEveryChange() {
        assertThatThrownBy(() -> service.update("users", List.of(
                change("1", "username", "first", "edited"), change("2", "username", "outdated", "new")), 0, 100))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=1", String.class)).isEqualTo("first");
    }

    @Test
    void constraintFailureRollsBackEveryChange() {
        assertThatThrownBy(() -> service.update("users", List.of(
                change("1", "username", "first", "edited"), change("2", "username", "second", null)), 0, 100))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=1", String.class)).isEqualTo("first");
    }

    @Test
    void rejectsProtectedAndUnknownColumnsAndUnknownIds() {
        for (String column : List.of("id", "password", "username` = 'injected' --")) {
            assertThatThrownBy(() -> service.update("users", List.of(change("1", column, "1", "2")), 0, 100))
                    .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> service.update("users", List.of(change("99", "username", "x", "y")), 0, 100))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void preservesNullAndEmptyStringAsDifferentValues() {
        service.update("users", List.of(change("1", "phonenumber", null, "")), 0, 100);
        assertThat(jdbc.queryForObject("SELECT phonenumber FROM users WHERE id=1", String.class)).isEmpty();
    }

    @Test
    void savesTypedValuesWithoutLosingBigintPrecisionAndRejectsInvalidNumbers() {
        jdbc.execute("CREATE TABLE tickets (id BIGINT PRIMARY KEY, price INT, event_id BIGINT, start_time TIMESTAMP)");
        jdbc.update("INSERT INTO tickets VALUES (1, 100, 9007199254740993, NULL)");
        var page = service.update("tickets", List.of(change("1", "price", "100", "00200")), 0, 100);
        assertThat(page.rows().getFirst()).contains("200", "9007199254740993");
        assertThatThrownBy(() -> service.update("tickets", List.of(change("1", "price", "200", "12abc")), 0, 100))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT price FROM tickets WHERE id=1", Integer.class)).isEqualTo(200);
    }

    @Test
    void rejectsImpossibleDatesInsteadOfSilentlyChangingThem() {
        jdbc.execute("CREATE TABLE events (id BIGINT PRIMARY KEY, start_date DATE, start_time TIMESTAMP)");
        jdbc.update("INSERT INTO events VALUES (1, NULL, NULL)");
        assertThatThrownBy(() -> service.update("events", List.of(change("1", "start_date", null, "2026-02-30")), 0, 100))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.update("events", List.of(change("1", "start_time", null, "2026-02-30 12:00:00")), 0, 100))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void insertsWithGeneratedIdAndDeletesWithPageCorrection() {
        jdbc.execute("CREATE TABLE events (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, category VARCHAR(100) DEFAULT 'concert')");
        var added = service.save("events", List.of(), List.of(Map.of("name", "new event")), List.of(), 0, 1);
        assertThat(added.rows().getFirst()).containsExactly("1", "new event", "concert");
        var last = service.save("events", List.of(), List.of(Map.of("name", "second event")), List.of(), 0, 1);
        assertThat(last.page()).isEqualTo(1);
        var removed = service.save("events", List.of(), List.of(), List.of(new AdminTableService.RowDeletion("2",
                Map.of("id", "2", "name", "second event", "category", "concert"))), 1, 1);
        assertThat(removed.page()).isZero();
        assertThat(removed.totalRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE id=2", Long.class)).isZero();
    }

    @Test
    void deletionAndUpdateRollBackWhenInsertFails() {
        jdbc.execute("CREATE TABLE events (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE)");
        jdbc.update("INSERT INTO events(name) VALUES ('first'), ('second')");
        assertThatThrownBy(() -> service.save("events", List.of(change("2", "name", "second", "edited")),
                List.of(Map.of("name", "edited")), List.of(new AdminTableService.RowDeletion("1", Map.of("id", "1", "name", "first"))), 0, 100))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForList("SELECT name FROM events ORDER BY id", String.class)).containsExactly("first", "second");
    }

    @Test
    void rejectsStaleDeletionAndForeignKeyDeletion() {
        jdbc.execute("CREATE TABLE events (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        jdbc.execute("CREATE TABLE tickets (id BIGINT PRIMARY KEY, event_id BIGINT REFERENCES events(id))");
        jdbc.update("INSERT INTO events(name) VALUES ('first')");
        jdbc.update("INSERT INTO tickets VALUES (1, 1)");
        for (String original : List.of("outdated", "first")) {
            assertThatThrownBy(() -> service.save("events", List.of(), List.of(),
                    List.of(new AdminTableService.RowDeletion("1", Map.of("id", "1", "name", original))), 0, 100))
                    .isInstanceOf(ApiException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM events", Long.class)).isEqualTo(1);
    }

    @Test
    void createsUserWithEncodedPasswordAndDefaultRole() {
        jdbc.execute("DROP TABLE users");
        jdbc.execute("CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100) NOT NULL, password VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL, customer_key VARCHAR(255) NOT NULL)");
        var page = service.save("users", List.of(), List.of(Map.of("username", "new user", "password", "password123", "customer_key", "client-value")), List.of(), 0, 100);
        String hash = jdbc.queryForObject("SELECT password FROM users", String.class);
        assertThat(org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("password123", hash)).isTrue();
        assertThat(page.columns()).doesNotContain("password");
        assertThat(page.rows().getFirst()).contains("USER", "ACTIVE");
        String customerKey = jdbc.queryForObject("SELECT customer_key FROM users WHERE id=1", String.class);
        assertThat(customerKey).startsWith("customer_").isNotEqualTo("client-value");
        assertThat(page.createFields()).extracting(AdminTableService.CreateField::name).doesNotContain("customer_key");
        service.save("users", List.of(), List.of(Map.of("username", "second user", "password", "password123")), List.of(), 0, 100);
        assertThat(jdbc.queryForObject("SELECT customer_key FROM users WHERE id=2", String.class)).startsWith("customer_").isNotEqualTo(customerKey);
        assertThatThrownBy(() -> service.update("users", List.of(change("1", "customer_key", customerKey, "edited")), 0, 100))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsInsertedPrimaryKeyAndUnknownColumn() {
        for (String column : List.of("id", "unknown`")) {
            assertThatThrownBy(() -> service.save("users", List.of(), List.of(Map.of(column, "1")), List.of(), 0, 100))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void restrictsUserChoicesOnBothCreationAndUpdate() {
        jdbc.execute("ALTER TABLE users ADD role VARCHAR(20) DEFAULT 'USER'");
        jdbc.execute("ALTER TABLE users ADD status VARCHAR(20) DEFAULT 'ACTIVE'");
        for (var entry : Map.of("role", "DIRECTOR", "status", "DELETED").entrySet()) {
            assertThatThrownBy(() -> service.save("users", List.of(), List.of(Map.of("username", "new", "password", "secret", entry.getKey(), entry.getValue())), List.of(), 0, 100))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.update("users", List.of(change("1", entry.getKey(), entry.getKey().equals("role") ? "USER" : "ACTIVE", entry.getValue())), 0, 100))
                    .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> service.update("users", List.of(change("1", "status", "ACTIVE", null)), 0, 100))
                .isInstanceOf(ApiException.class);
        service.update("users", List.of(change("1", "status", "ACTIVE", "INACTIVE")), 0, 100);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=1", String.class)).isEqualTo("INACTIVE");
    }
}
