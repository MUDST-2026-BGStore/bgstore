package com.chanakanlabs.theparlor.account.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.theparlor.account.AccountNotProvisionedException;
import com.chanakanlabs.theparlor.account.AccountRegistration;
import com.chanakanlabs.theparlor.account.internal.domain.Account;
import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentity;
import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentityId;
import com.chanakanlabs.theparlor.account.internal.persistence.AccountIdentityRepository;
import com.chanakanlabs.theparlor.account.internal.persistence.AccountRepository;
import com.chanakanlabs.theparlor.account.spi.AccountCommitments;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.identity.UserProfile;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AccountServiceTests {
  @ParameterizedTest
  @ValueSource(strings = {"+66898765432", "+442079460018"})
  void updatesThePhoneWithoutChangingTheLocaleOrName(String phone) {
    var currentUser = mock(CurrentUser.class);
    var identities = mock(AccountIdentityRepository.class);
    var service =
        new AccountService(
            currentUser,
            mock(AccountRepository.class),
            identities,
            mock(JdbcTemplate.class),
            List.of());
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
    var service =
        new AccountService(currentUser, accounts, identities, mock(JdbcTemplate.class), List.of());
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
    var service =
        new AccountService(currentUser, accounts, identities, mock(JdbcTemplate.class), List.of());
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
            currentUser,
            mock(AccountRepository.class),
            identities,
            mock(JdbcTemplate.class),
            List.of());
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
            currentUser,
            mock(AccountRepository.class),
            identities,
            mock(JdbcTemplate.class),
            List.of());
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
    assertThatThrownBy(service::deleteCurrent).isInstanceOf(AccountNotProvisionedException.class);
  }

  @Test
  void deletesAccountWhenNoOpenCommitmentsExist() {
    var currentUser = mock(CurrentUser.class);
    var accounts = mock(AccountRepository.class);
    var identities = mock(AccountIdentityRepository.class);
    var commitment = mock(AccountCommitments.class);
    var service =
        new AccountService(
            currentUser, accounts, identities, mock(JdbcTemplate.class), List.of(commitment));
    var account =
        Account.create(
            "Test", "User", "+66812345678", java.util.Map.of("preferredLocale", "en-US"));
    when(currentUser.require())
        .thenReturn(new UserProfile("issuer", "subject", "test", null, null, true, List.of()));
    when(identities.findById(new AccountIdentityId("issuer", "subject")))
        .thenReturn(Optional.of(new AccountIdentity("issuer", "subject", account)));
    when(commitment.hasOpenCommitments("subject")).thenReturn(false);

    service.deleteCurrent();

    verify(accounts).delete(account);
    verify(accounts).flush();
  }

  @Test
  void refusesDeletionWithConflictWhenAccountHasOpenCommitments() {
    var currentUser = mock(CurrentUser.class);
    var accounts = mock(AccountRepository.class);
    var identities = mock(AccountIdentityRepository.class);
    var commitment = mock(AccountCommitments.class);
    var service =
        new AccountService(
            currentUser, accounts, identities, mock(JdbcTemplate.class), List.of(commitment));
    var account =
        Account.create(
            "Test", "User", "+66812345678", java.util.Map.of("preferredLocale", "en-US"));
    when(currentUser.require())
        .thenReturn(new UserProfile("issuer", "subject", "test", null, null, true, List.of()));
    when(identities.findById(new AccountIdentityId("issuer", "subject")))
        .thenReturn(Optional.of(new AccountIdentity("issuer", "subject", account)));
    when(commitment.hasOpenCommitments("subject")).thenReturn(true);

    assertThatThrownBy(service::deleteCurrent)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> {
              assertThat(ex.getStatusCode().value()).isEqualTo(409);
              assertThat(ex.getReason()).isEqualTo("Account has an open reservation or order");
            });
    org.mockito.Mockito.verifyNoInteractions(accounts);
  }

  @Test
  void searchListsAccountsWhenQueryIsBlankEscapesWildcardsAndCapsLimitAtTwenty() {
    var identities = mock(AccountIdentityRepository.class);
    var service =
        new AccountService(
            mock(CurrentUser.class),
            mock(AccountRepository.class),
            identities,
            mock(JdbcTemplate.class),
            List.of());

    var directory =
        Account.create(
            "Ada", "Lovelace", "+66812345678", java.util.Map.of("preferredLocale", "en-US"));
    when(identities.search("%%", PageRequest.of(0, 10)))
        .thenReturn(List.of(new AccountIdentity("issuer", "sub-1", directory)));
    when(identities.search("%a%", PageRequest.of(0, 10)))
        .thenReturn(List.of(new AccountIdentity("issuer", "sub-1", directory)));

    // Blank and short queries list the first page so administrators can browse everyone onboarded.
    assertThat(service.search(" ", 10))
        .containsExactly(
            new com.chanakanlabs.theparlor.account.AccountDirectory.AccountMatch(
                "sub-1", "Ada Lovelace", "+66812345678"));
    assertThat(service.search("a", 10))
        .containsExactly(
            new com.chanakanlabs.theparlor.account.AccountDirectory.AccountMatch(
                "sub-1", "Ada Lovelace", "+66812345678"));
    assertThat(service.search("ab", 0)).isEmpty();
    org.mockito.Mockito.verify(identities, times(1)).search("%%", PageRequest.of(0, 10));
    org.mockito.Mockito.verify(identities, times(1)).search("%a%", PageRequest.of(0, 10));

    var account =
        Account.create(
            "Ada", "Lovelace", "+66812345678", java.util.Map.of("preferredLocale", "en-US"));
    when(identities.search("%100\\%\\_a\\\\b%", PageRequest.of(0, 20)))
        .thenReturn(List.of(new AccountIdentity("issuer", "sub-1", account)));

    var matches = service.search("  100%_A\\b  ", 99);
    assertThat(matches)
        .containsExactly(
            new com.chanakanlabs.theparlor.account.AccountDirectory.AccountMatch(
                "sub-1", "Ada Lovelace", "+66812345678"));
  }
}
