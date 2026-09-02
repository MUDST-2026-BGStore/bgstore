package com.chanakanlabs.bgstore.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.identity.ApplicationRole;
import com.chanakanlabs.bgstore.identity.AuthenticatedIdentity;
import com.chanakanlabs.bgstore.identity.CurrentIdentityProvider;
import com.chanakanlabs.bgstore.identity.IdentityAccountService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

  private static final String SUBJECT = "a9c7022e-a678-4d50-aa1b-69c917001234";

  @Mock private CurrentIdentityProvider currentIdentityProvider;
  @Mock private IdentityAccountService identityAccounts;
  @Mock private ClientProfileRepository clientProfiles;

  private CurrentUserService service;

  @BeforeEach
  void setUp() {
    service = new CurrentUserService(currentIdentityProvider, identityAccounts, clientProfiles);
  }

  @Test
  void createsAndReturnsAnIncompleteProfileForAClient() {
    AuthenticatedIdentity client = identity(Set.of(ApplicationRole.CLIENT));
    when(currentIdentityProvider.currentIdentity()).thenReturn(client);
    when(clientProfiles.findBySubject(SUBJECT))
        .thenReturn(Optional.of(new ClientProfileData(null, false)));

    var currentUser = service.currentUser();

    assertThat(currentUser.onboardingRequired()).isTrue();
    assertThat(currentUser.clientProfile()).isEqualTo(new ClientProfileData(null, false));
    verify(identityAccounts).synchronize(client);
    verify(clientProfiles).createIfAbsent(SUBJECT);
  }

  @Test
  void skipsClientProfilesForStaffEvenWhenTheyAlsoHaveTheDefaultClientRole() {
    AuthenticatedIdentity staff = identity(Set.of(ApplicationRole.CLIENT, ApplicationRole.STAFF));
    when(currentIdentityProvider.currentIdentity()).thenReturn(staff);

    var currentUser = service.currentUser();

    assertThat(currentUser.clientProfile()).isNull();
    assertThat(currentUser.onboardingRequired()).isFalse();
    verify(clientProfiles, never()).createIfAbsent(SUBJECT);
  }

  @Test
  void completesAClientProfileUsingANormalizedInternationalNumber() {
    AuthenticatedIdentity client = identity(Set.of(ApplicationRole.CLIENT));
    when(currentIdentityProvider.currentIdentity()).thenReturn(client);
    when(clientProfiles.complete(SUBJECT, "+66812345678"))
        .thenReturn(new ClientProfileData("+66812345678", true));

    var profile = service.completeClientProfile("+66", "081 234-5678");

    assertThat(profile.phone()).isEqualTo("+66812345678");
    verify(identityAccounts).synchronize(client);
    verify(clientProfiles).createIfAbsent(SUBJECT);
  }

  @Test
  void acceptsForeignNumbersWhenTheCountryCodeIsExplicit() {
    AuthenticatedIdentity client = identity(Set.of(ApplicationRole.CLIENT));
    when(currentIdentityProvider.currentIdentity()).thenReturn(client);
    when(clientProfiles.complete(SUBJECT, "+14155552671"))
        .thenReturn(new ClientProfileData("+14155552671", true));

    var profile = service.completeClientProfile("+1", "415 555 2671");

    assertThat(profile.phone()).isEqualTo("+14155552671");
  }

  @Test
  void rejectsInvalidNumbersAndNonClientProfileUpdates() {
    when(currentIdentityProvider.currentIdentity())
        .thenReturn(identity(Set.of(ApplicationRole.CLIENT)));

    assertThatThrownBy(() -> service.completeClientProfile("+66", "abc123"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("valid phone");

    when(currentIdentityProvider.currentIdentity())
        .thenReturn(identity(Set.of(ApplicationRole.STAFF)));
    assertThatThrownBy(() -> service.completeClientProfile("+66", "0812345678"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("not required");
  }

  @Test
  void requiresOnboardingUntilTheClientProfileIsComplete() {
    AuthenticatedIdentity client = identity(Set.of(ApplicationRole.CLIENT));
    when(clientProfiles.findBySubject(SUBJECT))
        .thenReturn(
            Optional.<ClientProfileData>empty(),
            Optional.of(new ClientProfileData("+66812345678", true)));

    assertThat(service.requiresOnboarding(client)).isTrue();
    assertThat(service.requiresOnboarding(client)).isFalse();
    assertThat(service.requiresOnboarding(identity(Set.of(ApplicationRole.MANAGER)))).isFalse();
  }

  private static AuthenticatedIdentity identity(Set<ApplicationRole> roles) {
    return new AuthenticatedIdentity(
        SUBJECT, "client@example.test", "client@example.test", "Local", "Client", roles);
  }
}
