package com.chanakanlabs.theparlor.account.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanakanlabs.theparlor.account.internal.domain.Account;
import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentity;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
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
  @Autowired AccountIdentityRepository identities;
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

  @Test
  void searchMatchesNameOrPhoneCaseInsensitivelyAndOrdersByNameThenSubject() {
    var alice =
        accounts.saveAndFlush(
            Account.create("Alice", "Wong", "+66811112222", Map.of("preferredLocale", "en-US")));
    var bob =
        accounts.saveAndFlush(
            Account.create("Bob", "Smith", "+66899990000", Map.of("preferredLocale", "en-US")));
    identities.saveAndFlush(new AccountIdentity("issuer", "sub-b", bob));
    identities.saveAndFlush(new AccountIdentity("issuer", "sub-a2", alice));
    identities.saveAndFlush(new AccountIdentity("issuer", "sub-a1", alice));
    entityManager.clear();

    var byName = identities.search("%alice w%", PageRequest.of(0, 20));
    assertThat(byName).extracting(AccountIdentity::getSubject).containsExactly("sub-a1", "sub-a2");

    var byPhone = identities.search("%99900%", PageRequest.of(0, 20));
    assertThat(byPhone).extracting(AccountIdentity::getSubject).containsExactly("sub-b");
  }
}
