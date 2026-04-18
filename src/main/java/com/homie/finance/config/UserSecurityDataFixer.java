package com.homie.finance.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserSecurityDataFixer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public UserSecurityDataFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("ALTER TABLE categories ADD COLUMN IF NOT EXISTS icon varchar(255) DEFAULT 'category'");
        jdbcTemplate.execute("UPDATE categories SET icon = 'category' WHERE icon IS NULL OR btrim(icon) = ''");
        jdbcTemplate.execute("ALTER TABLE categories ALTER COLUMN icon SET DEFAULT 'category'");
        jdbcTemplate.execute("ALTER TABLE categories ALTER COLUMN icon SET NOT NULL");

        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret varchar(255)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_ip varchar(255)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS is_2fa_enabled boolean DEFAULT false");
        jdbcTemplate.execute("UPDATE users SET is_2fa_enabled = false WHERE is_2fa_enabled IS NULL");
        jdbcTemplate.execute("UPDATE users SET is_2fa_enabled = false WHERE is_2fa_enabled = true AND (totp_secret IS NULL OR btrim(totp_secret) = '')");
        jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN is_2fa_enabled SET DEFAULT false");
        jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN is_2fa_enabled SET NOT NULL");
    }
}
