package com.chanakanlabs.theparlor.account.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.theparlor.account.AccountNotProvisionedException;
import com.chanakanlabs.theparlor.account.AccountRegistration;
import com.chanakanlabs.theparlor.account.internal.domain.Account;
import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentity;
import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentityId;
import com.chanakanlabs.theparlor.account.internal.persistence.AccountIdentityRepository;
import com.chanakanlabs.theparlor.account.internal.persistence.AccountRepository;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.identity.UserProfile;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class AccountServiceTests {
  @ParameterizedTest
  @ValueSource(strings = {"+66898765432", "+442079460018"})
  void updatesThePhoneWithoutChangingTheLocaleOrName(String phone) {
    var currentUser = mock(CurrentUser.class);
    var identities = mock(AccountIdentityRepository.class);
    var service =
        new AccountService(
            currentUser, mock(AccountRepository.class), identities, mock(JdbcTemplate.class));
    var account =
        Account.create(
            "Test", "User", "+66812345678", java.util.Map.of("preferredLocale", "th-TH"));
    when(currentUser.require())
        .thenReturn(
            new UserProfile(
                "https://issuer.example", "user-1", "test", null, null, true, List.of()));
    when(identities.findById(new AccountIdentityId("https://issuer.example", "user-1")))
        .thenReturn(Optional.of(new AccountIdentity("https://issuer.example", "user-1", account)));
    var profile = service.updateCurrentProfile(null, null, phone);
    assertThat(profile.getPhoneNumber()).isEqualTo(phone);
    assertThat(profile.getPreferences().getPreferredLocale().getValue()).isEqualTo("th-TH");
    assertThat(profile.getFirstName()).isEqualTo("Test");
    assertThat(profile.getLastName()).isEqualTo("User");
  }

  @ParameterizedTest
  @ValueSource(strings = {"en-US", "th-TH"})
  void updatesOnlyTheCurrentAccountsLocale(String locale) {
    var currentUser = mock(CurrentUser.class);
    var accounts = mock(AccountRepository.class);
    var identities = mock(AccountIdentityRepository.class);
    var service = new AccountService(currentUser, accounts, identities, mock(JdbcTemplate.class));
    var account =
        Account.create(
            "Test",
            "User",
            "+66812345678",
            java.util.Map.of("preferredLocale", locale.equals("en-US") ? "th-TH" : "en-US"));
    when(currentUser.require())
        .thenReturn(
            new UserProfile(
                "https://issuer.example", "user-1", "test", null, null, true, List.of()));
    when(identities.findById(new AccountIdentityId("https://issuer.example", "user-1")))
        .thenReturn(Optional.of(new AccountIdentity("https://issuer.example", "user-1", account)));

    var settings = service.updateCurrentSettings(java.util.Map.of("preferredLocale", locale));

    assertThat(account.getPreferences().get("preferredLocale")).isEqualTo(locale);
    assertThat(settings.getPreferences().getPreferredLocale().getValue()).isEqualTo(locale);
    var profile = service.findCurrentProfile().orElseThrow();
    assertThat(profile.getFirstName()).isEqualTo("Test");
    assertThat(profile.getLastName()).isEqualTo("User");
    assertThat(profile.getPhoneNumber()).isEqualTo("+66812345678");
  }

  @ParameterizedTest
  @ValueSource(strings = {"en-US", "th-TH"})
  void savesTheSelectedLocaleWhenProvisioningAnAccount(String locale) {
    var currentUser = mock(CurrentUser.class);
    var accounts = mock(AccountRepository.class);
    var identities = mock(AccountIdentityRepository.class);
    var entityManager = mock(EntityManager.class);
    var service = new AccountService(currentUser, accounts, identities, mock(JdbcTemplate.class));
    ReflectionTestUtils.setField(service, "entityManager", entityManager);
    when(currentUser.require())
        .thenReturn(
            new UserProfile(
                "https://issuer.example", "user-1", "test", null, null, true, List.of()));
    when(identities.findById(any(AccountIdentityId.class))).thenReturn(Optional.empty());
    when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var profile =
        service.provisionForCurrentIdentity(
            new AccountRegistration(
                "Test", "User", "+66812345678", java.util.Map.of("preferredLocale", locale)));

    var savedAccount = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(savedAccount.capture());
    assertThat(savedAccount.getValue().getPreferences().get("preferredLocale")).isEqualTo(locale);
    assertThat(profile.getPreferences().getPreferredLocale().getValue()).isEqualTo(locale);
    var identity = ArgumentCaptor.forClass(AccountIdentity.class);
    verify(entityManager).persist(identity.capture());
    assertThat(identity.getValue().getAccount()).isSameAs(savedAccount.getValue());
  }

  @Test
  void updatesNamesIndependentlyAndPreservesPhoneAndPreferences() {
    var currentUser = mock(CurrentUser.class);
    var identities = mock(AccountIdentityRepository.class);
    var service =
        new AccountService(
            currentUser, mock(AccountRepository.class), identities, mock(JdbcTemplate.class));
    var account =
        Account.create(
            "Test", "User", "+66812345678", java.util.Map.of("preferredLocale", "th-TH"));
    ReflectionTestUtils.setField(
        account,
        "preferences",
        new java.util.HashMap<>(java.util.Map.of("theme", "dark", "preferredLocale", "th-TH")));
    when(currentUser.require())
        .thenReturn(new UserProfile("issuer", "subject", "test", null, null, true, List.of()));
    when(identities.findById(new AccountIdentityId("issuer", "subject")))
        .thenReturn(Optional.of(new AccountIdentity("issuer", "subject", account)));

    var renamed = service.updateCurrentProfile("Alice", null, null);
    assertThat(renamed.getFirstName()).isEqualTo("Alice");
    assertThat(renamed.getLastName()).isEqualTo("User");
    var profile = service.updateCurrentProfile(null, "Smith", null);
    assertThat(profile.getFirstName()).isEqualTo("Alice");
    assertThat(profile.getLastName()).isEqualTo("Smith");
    assertThat(profile.getPhoneNumber()).isEqualTo("+66812345678");
    assertThat(profile.getPreferences().getPreferredLocale().getValue()).isEqualTo("th-TH");
    assertThat(profile.getPreferences().getAdditionalProperty("theme")).isEqualTo("dark");
    service.updateCurrentSettings(java.util.Map.of("preferredLocale", "en-US"));
    assertThat(account.getPreferences()).containsEntry("theme", "dark");
  }

  @Test
  void requiresAnExistingAccountForProfileAndSettingsOperations() {
    var currentUser = mock(CurrentUser.class);
    var identities = mock(AccountIdentityRepository.class);
    var service =
        new AccountService(
            currentUser, mock(AccountRepository.class), identities, mock(JdbcTemplate.class));
    when(currentUser.require())
        .thenReturn(new UserProfile("issuer", "subject", "test", null, null, true, List.of()));
    when(identities.findById(any(AccountIdentityId.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateCurrentProfile("Alice", null, null))
        .isInstanceOf(AccountNotProvisionedException.class);
    assertThatThrownBy(service::getCurrentSettings)
        .isInstanceOf(AccountNotProvisionedException.class);
    assertThatThrownBy(
            () -> service.updateCurrentSettings(java.util.Map.of("preferredLocale", "en-US")))
        .isInstanceOf(AccountNotProvisionedException.class);
  }
}
