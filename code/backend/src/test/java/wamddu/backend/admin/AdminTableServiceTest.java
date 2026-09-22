package wamddu.backend.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import wamddu.backend.global.exception.ApiException;

import static org.assertj.core.api.Assertions.*;

class AdminTableServiceTest {
    private AdminTableService service;

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:admin_tables;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", ""));
        // Keep one connection open via DB_CLOSE_DELAY for the duration of these tests.
        jdbc.execute("SET DB_CLOSE_DELAY -1");
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username VARCHAR(100), password VARCHAR(100))");
        jdbc.execute("CREATE TABLE events (id BIGINT PRIMARY KEY, name VARCHAR(100))");
        jdbc.update("INSERT INTO users VALUES (2, 'second', 'secret2'), (1, 'first', 'secret1')");
        service = new AdminTableService(jdbc);
    }

    @Test
    void listsOnlyExistingApplicationTablesIncludingPriceHistory() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:admin_tables;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", ""));
        jdbc.execute("CREATE TABLE ticket_price_history (id BIGINT PRIMARY KEY, ticket_id BIGINT, price INT)");
        jdbc.execute("CREATE TABLE internal_secrets (id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO ticket_price_history VALUES (1, 3, 25000)");
        assertThat(service.tables()).containsExactly("users", "events", "ticket_price_history");
        assertThat(service.read("ticket_price_history", 0, 100).rows())
                .containsExactly(java.util.Arrays.asList("1", "3", "25000"));
    }

    @Test
    void placesIdFirstAndDescribesCreationInputs() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:admin_tables;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", ""));
        jdbc.execute("DROP TABLE events");
        jdbc.execute("CREATE TABLE events (name VARCHAR(100) NOT NULL, start_date DATE, id BIGINT AUTO_INCREMENT PRIMARY KEY, price INT DEFAULT 0)");
        jdbc.update("INSERT INTO events(name) VALUES ('concert')");
        var page = service.read("events", 0, 100);
        assertThat(page.columns()).containsExactly("id", "name", "start_date", "price");
        assertThat(page.rows().getFirst()).containsExactly("1", "concert", null, "0");
        assertThat(page.createFields()).containsExactly(
                new AdminTableService.CreateField("name", "text", true),
                new AdminTableService.CreateField("start_date", "date", false),
                new AdminTableService.CreateField("price", "number", false));
    }

    @Test
    void returnsOrderedPagesWithoutPassword() {
        var page = service.read("users", 1, 1);
        assertThat(page.totalRows()).isEqualTo(2);
        assertThat(page.columns()).containsExactly("id", "username");
        assertThat(page.rows()).containsExactly(java.util.Arrays.asList("2", "second"));
    }

    @Test
    void emptyTableStillReturnsColumns() {
        var page = service.read("events", 0, 100);
        assertThat(page.columns()).containsExactly("id", "name");
        assertThat(page.rows()).isEmpty();
        assertThat(page.totalRows()).isZero();
    }

    @Test
    void rejectsUnknownTablesAndInvalidPagination() {
        assertThatThrownBy(() -> service.read("users; DROP TABLE users", 0, 100)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.read("users", -1, 100)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.read("users", 0, 101)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.read("users", 0, 0)).isInstanceOf(ApiException.class);
    }
}
