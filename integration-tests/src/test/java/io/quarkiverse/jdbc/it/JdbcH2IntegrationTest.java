package io.quarkiverse.jdbc.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jdbc.runtime.core.BeanPropertyRowMapper;
import io.quarkiverse.jdbc.runtime.core.JdbcOperations;
import io.quarkiverse.jdbc.runtime.core.JdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.namedparam.NamedParameterJdbcOperations;
import io.quarkiverse.jdbc.runtime.core.namedparam.NamedParameterJdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.simple.JdbcClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class JdbcH2IntegrationTest {

    @Inject
    DataSource dataSource;

    @Inject
    JdbcTemplate jdbcTemplate;

    @Inject
    JdbcOperations jdbcOperations;

    @Inject
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Inject
    NamedParameterJdbcOperations namedParameterJdbcOperations;

    @Inject
    JdbcClient jdbcClient;

    @Inject
    @io.quarkus.agroal.DataSource("extra")
    JdbcTemplate extraJdbcTemplate;

    @Inject
    @io.quarkus.agroal.DataSource("extra")
    NamedParameterJdbcTemplate extraNamedParameterJdbcTemplate;

    @Inject
    @io.quarkus.agroal.DataSource("auto")
    JdbcTemplate autoJdbcTemplate;

    @Test
    void injectsJdbcBeansAndExecutesQueriesAgainstH2() {
        assertNotNull(this.dataSource);
        assertNotNull(this.jdbcTemplate);
        assertNotNull(this.jdbcOperations);
        assertNotNull(this.namedParameterJdbcTemplate);
        assertNotNull(this.namedParameterJdbcOperations);
        assertNotNull(this.jdbcClient);

        Integer bookCount = this.jdbcTemplate.queryForObject("select count(*) from book", Integer.class);
        Integer tenantBookCount = this.namedParameterJdbcOperations.queryForObject(
                "select count(*) from book where tenant = :tenant", Map.of("tenant", "a"), Integer.class);
        String storeName = this.namedParameterJdbcTemplate.queryForObject(
                "select name from book_store where id = :id", Map.of("id", 1), String.class);
        BookSummary book = this.jdbcTemplate.queryForObject(
                "select id, name, edition, tenant from book where id = ?",
                BeanPropertyRowMapper.newInstance(BookSummary.class), 4);
        Integer typeScriptCount = this.jdbcClient.sql("select count(*) from book where name like :name")
                .param("name", "%TypeScript")
                .query(Integer.class)
                .single();
        int updated = this.jdbcClient.sql("update book set price = price + 1 where id = ?")
                .param(1)
                .update();

        assertEquals(12, bookCount);
        assertEquals(6, tenantBookCount);
        assertEquals("O'REILLY", storeName);
        assertNotNull(book);
        assertEquals(4L, book.getId());
        assertEquals("Effective TypeScript", book.getName());
        assertEquals(1, book.getEdition());
        assertEquals("b", book.getTenant());
        assertEquals(6, typeScriptCount);
        assertEquals(1, updated);
    }

    @Test
    void initializesNamedDatasourceAgainstH2() {
        assertNotNull(this.extraJdbcTemplate);
        assertNotNull(this.extraNamedParameterJdbcTemplate);

        Integer itemCount = this.extraJdbcTemplate.queryForObject("select count(*) from extra_item", Integer.class);
        String itemName = this.extraNamedParameterJdbcTemplate.queryForObject(
                "select name from extra_item where id = :id", Map.of("id", 2), String.class);

        assertEquals(2, itemCount);
        assertEquals("beta", itemName);
    }

    @Test
    void initializesDatasourceWithDefaultSqlScriptsAndPlatformScripts() {
        assertNotNull(this.autoJdbcTemplate);

        Integer bookCount = this.autoJdbcTemplate.queryForObject("select count(*) from auto_book", Integer.class);
        String platformMarker = this.autoJdbcTemplate.queryForObject(
                "select name from auto_platform_marker where id = ?", String.class, 1);

        assertEquals(2, bookCount);
        assertEquals("h2-platform", platformMarker);
    }

    public static class BookSummary {

        private long id;

        private String name;

        private int edition;

        private String tenant;

        public long getId() {
            return this.id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getEdition() {
            return this.edition;
        }

        public void setEdition(int edition) {
            this.edition = edition;
        }

        public String getTenant() {
            return this.tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
    }

}
