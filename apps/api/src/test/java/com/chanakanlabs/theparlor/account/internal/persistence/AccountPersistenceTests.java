package com.chanakanlabs.theparlor.account.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanakanlabs.theparlor.account.internal.domain.Account;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountPersistenceTests {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.3");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired AccountRepository accounts;
  @Autowired EntityManager entityManager;

  @Test
  void localeLivesInPreferencesAndSurvivesFlushClearAndReload() {
    var account =
        accounts.saveAndFlush(
            Account.create(
                "First",
                "Last",
                "123",
                Map.of(
                    "preferredLocale",
                    "en-US",
                    "theme",
                    "dark",
                    "nested",
                    Map.of("enabled", true))));
    var id = account.getId();
    entityManager.clear();

    var loaded = accounts.findById(id).orElseThrow();
    assertThat(loaded.getPreferences()).containsEntry("preferredLocale", "en-US");
    loaded.updatePreferences(Map.of("preferredLocale", "th-TH"));
    accounts.flush();
    entityManager.clear();

    assertThat(accounts.findById(id).orElseThrow().getPreferences())
        .containsEntry("preferredLocale", "th-TH")
        .containsEntry("theme", "dark")
        .containsEntry("nested", Map.of("enabled", true));
  }
}
