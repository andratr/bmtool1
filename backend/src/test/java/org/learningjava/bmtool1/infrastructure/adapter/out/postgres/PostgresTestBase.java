package org.learningjava.bmtool1.infrastructure.adapter.out.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

public abstract class PostgresTestBase {
    protected static PostgreSQLContainer<?> pg;
    protected static HikariDataSource ds;

    @BeforeAll
    static void startPg() {
        pg = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("bmtool")
                .withUsername("app")
                .withPassword("app_pw");
        pg.start();

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(pg.getJdbcUrl());
        cfg.setUsername(pg.getUsername());
        cfg.setPassword(pg.getPassword());
        cfg.setMaximumPoolSize(4);
        cfg.setMinimumIdle(1);
        ds = new HikariDataSource(cfg);
    }

    @AfterAll
    static void stopPg() {
        if (ds != null) ds.close();
        if (pg != null) pg.stop();
    }

    protected DataSource dataSource() {
        return ds;
    }
}
