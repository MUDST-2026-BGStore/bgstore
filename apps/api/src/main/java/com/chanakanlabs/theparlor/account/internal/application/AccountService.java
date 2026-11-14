package com.chanakanlabs.theparlor.account.internal.application;

import static com.chanakanlabs.theparlor.account.internal.domain.AccountPreferencePolicy.*;

import com.chanakanlabs.theparlor.account.AccountDirectory;
import com.chanakanlabs.theparlor.account.AccountNotProvisionedException;
import com.chanakanlabs.theparlor.account.AccountProvisioning;
import com.chanakanlabs.theparlor.account.AccountRegistration;
import com.chanakanlabs.theparlor.account.CurrentAccount;
import com.chanakanlabs.theparlor.account.internal.domain.Account;
import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentity;
import com.chanakanlabs.theparlor.account.internal.domain.AccountIdentityId;
import com.chanakanlabs.theparlor.account.internal.persistence.AccountIdentityRepository;
import com.chanakanlabs.theparlor.account.internal.persistence.AccountRepository;
import com.chanakanlabs.theparlor.account.spi.AccountCommitments;
import com.chanakanlabs.theparlor.contracts.model.AccountPreferences;
import com.chanakanlabs.theparlor.contracts.model.AccountSettings;
import com.chanakanlabs.theparlor.contracts.model.PreferredLocale;
import com.chanakanlabs.theparlor.contracts.model.Profile;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.identity.UserProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService implements CurrentAccount, AccountProvisioning, AccountDirectory {

  private final CurrentUser currentUser;
  private final AccountRepository accounts;
  private final AccountIdentityRepository identities;
  private final JdbcTemplate jdbc;
  private final List<AccountCommitments> commitments;

  @PersistenceContext private EntityManager entityManager;

  public AccountService(
      CurrentUser currentUser,
      AccountRepository accounts,
      AccountIdentityRepository identities,
      JdbcTemplate jdbc,
      List<AccountCommitments> commitments) {
    this.currentUser = currentUser;
    this.accounts = accounts;
    this.identities = identities;
    this.jdbc = jdbc;
    this.commitments = commitments;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> findId() {
    return currentUser.find().flatMap(this::lookupId);
  }

  @Override
  @Transactional(readOnly = true)
  public UUID requireId() {
    var user = currentUser.require();

    return lookupId(user).orElseThrow(AccountNotProvisionedException::new);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Profile> findCurrentProfile() {
    var identity = currentUser.require();

    var key = new AccountIdentityId(identity.issuer(), identity.subject());

    return identities.findById(key).map(mapping -> toProfile(mapping.getAccount()));
  }

  /** Empty when no account, or more than one identity provider, uses the subject. */
  @Override
  @Transactional(readOnly = true)
  public Optional<AccountContact> findBySubject(String subject) {
    var matches = identities.findBySubject(subject);
    if (matches.size() != 1) return Optional.empty();
    var account = matches.getFirst().getAccount();
    return Optional.of(
        new AccountContact(
            account.getId(),
            (account.getFirstName() + " " + account.getLastName()).strip(),
            account.getPhoneNumber()));
  }

  static final int MAX_SEARCH_RESULTS = 20;

  @Override
  @Transactional(readOnly = true)
  public List<AccountMatch> search(String query, int limit) {
    String text = query == null ? "" : query.strip();
    if (limit < 1) return List.of();
    String escaped =
        text.isEmpty()
            ? ""
            : text
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    return identities
        .search("%" + escaped + "%", PageRequest.of(0, Math.min(limit, MAX_SEARCH_RESULTS)))
        .stream()
        .map(
            identity -> {
              var account = identity.getAccount();
              return new AccountMatch(
                  identity.getSubject(),
                  (account.getFirstName() + " " + account.getLastName()).strip(),
                  account.getPhoneNumber());
            })
        .toList();
  }

  private Optional<UUID> lookupId(UserProfile user) {
    var key = new AccountIdentityId(user.issuer(), user.subject());

    return identities.findById(key).map(AccountIdentity::getAccount).map(Account::getId);
  }

  @Override
  @Transactional
  public Profile provisionForCurrentIdentity(AccountRegistration registration) {

    var identity = currentUser.require();

    // Fast path if already provisioned.
    var existing = lookupAccount(identity);

    if (existing.isPresent()) {
      return toProfile(existing.get());
    }

    // Serialize provisioning for this identity.
    lockIdentity(identity.issuer(), identity.subject());

    // Check again after acquiring the lock.
    existing = lookupAccount(identity);

    if (existing.isPresent()) {
      return toProfile(existing.get());
    }

    // Create a new account with onboarding information.
    var account =
        accounts.save(
            Account.create(
                registration.firstName(),
                registration.lastName(),
                registration.phoneNumber(),
                registration.preferences()));

    // Associate the identity with the account.
    entityManager.persist(new AccountIdentity(identity.issuer(), identity.subject(), account));

    return toProfile(account);
  }

  @Transactional
  public Profile updateCurrentProfile(String firstName, String lastName, String phoneNumber) {
    var account =
        lookupAccount(currentUser.require()).orElseThrow(AccountNotProvisionedException::new);
    account.updateProfile(firstName, lastName, phoneNumber);
    return toProfile(account);
  }

  @Transactional(readOnly = true)
  public AccountSettings getCurrentSettings() {
    return toSettings(
        lookupAccount(currentUser.require()).orElseThrow(AccountNotProvisionedException::new));
  }

  @Transactional
  public AccountSettings updateCurrentSettings(Map<String, Object> preferences) {
    var account =
        lookupAccount(currentUser.require()).orElseThrow(AccountNotProvisionedException::new);
    account.updatePreferences(preferences);
    return toSettings(account);
  }

  @Transactional
  public void deleteCurrent() {
    var user = currentUser.require();
    var account = lookupAccount(user).orElseThrow(AccountNotProvisionedException::new);
    if (commitments.stream().anyMatch(c -> c.hasOpenCommitments(user.subject()))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Account has an open reservation or order");
    }
    accounts.delete(account);
    accounts.flush();
  }

  private static AccountSettings toSettings(Account account) {
    return new AccountSettings(toPreferences(account));
  }

  private Optional<Account> lookupAccount(UserProfile identity) {
    var key = new AccountIdentityId(identity.issuer(), identity.subject());

    return identities.findById(key).map(AccountIdentity::getAccount);
  }

  private static Profile toProfile(Account account) {
    return new Profile(
        account.getId(),
        account.getFirstName(),
        account.getLastName(),
        account.getPhoneNumber(),
        toPreferences(account));
  }

  private static AccountPreferences toPreferences(Account account) {
    var values = account.getPreferences();
    var preferences =
        new AccountPreferences(
            PreferredLocale.fromValue((String) values.get(PREFERRED_LOCALE_KEY)));
    values.forEach(
        (key, value) -> {
          if (!key.equals(PREFERRED_LOCALE_KEY)) preferences.putAdditionalProperty(key, value);
        });
    return preferences;
  }

  private void lockIdentity(String issuer, String subject) {
    String key = issuer.length() + ":" + issuer + subject;

    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (var statement =
                  connection.prepareStatement(
                      "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, key);
                statement.execute();
              }

              return null;
            });
  }
}
