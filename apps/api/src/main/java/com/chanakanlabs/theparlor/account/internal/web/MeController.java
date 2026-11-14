package com.chanakanlabs.theparlor.account.internal.web;

import static com.chanakanlabs.theparlor.account.internal.domain.AccountPreferencePolicy.*;

import com.chanakanlabs.theparlor.account.AccountNotProvisionedException;
import com.chanakanlabs.theparlor.account.AccountRegistration;
import com.chanakanlabs.theparlor.account.internal.application.AccountService;
import com.chanakanlabs.theparlor.contracts.api.AccountApi;
import com.chanakanlabs.theparlor.contracts.model.AccountPreferences;
import com.chanakanlabs.theparlor.contracts.model.AccountSettings;
import com.chanakanlabs.theparlor.contracts.model.CreateAccountRequest;
import com.chanakanlabs.theparlor.contracts.model.Profile;
import com.chanakanlabs.theparlor.contracts.model.UpdateAccountRequest;
import com.chanakanlabs.theparlor.contracts.model.UpdateAccountSettingsRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MeController implements AccountApi {
  private final AccountService accountService;

  MeController(AccountService accountService) {
    this.accountService = accountService;
  }

  @Override
  public Profile createMe(CreateAccountRequest createAccountRequest) {
    var registration =
        new AccountRegistration(
            createAccountRequest.getFirstName(),
            createAccountRequest.getLastName(),
            createAccountRequest.getPhoneNumber(),
            preferenceValues(createAccountRequest.getPreferences()));

    accountService.provisionForCurrentIdentity(registration);
    return accountService.findCurrentProfile().orElseThrow();
  }

  @Override
  public void deleteMe() {
    accountService.deleteCurrent();
  }

  @Override
  public Profile me() {
    var currentAccountProfile = accountService.findCurrentProfile();

    return currentAccountProfile.orElseThrow(AccountNotProvisionedException::new);
  }

  @Override
  public Profile updateMe(UpdateAccountRequest updateAccountRequest) {
    if (updateAccountRequest.getFirstName() == null
        && updateAccountRequest.getLastName() == null
        && updateAccountRequest.getPhoneNumber() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Supply firstName, lastName or phoneNumber");
    }
    return accountService.updateCurrentProfile(
        updateAccountRequest.getFirstName(),
        updateAccountRequest.getLastName(),
        updateAccountRequest.getPhoneNumber());
  }

  @Override
  public AccountSettings getMeSettings() {
    return accountService.getCurrentSettings();
  }

  @Override
  public AccountSettings updateMeSettings(UpdateAccountSettingsRequest request) {
    if (request.getPreferences() == null || request.getPreferences().getPreferredLocale() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Supply preferences." + PREFERRED_LOCALE_KEY);
    }
    return accountService.updateCurrentSettings(preferenceValues(request.getPreferences()));
  }

  private static Map<String, Object> preferenceValues(AccountPreferences preferences) {
    var values = new HashMap<String, Object>();
    if (preferences.getAdditionalProperties() != null)
      values.putAll(preferences.getAdditionalProperties());
    values.put(PREFERRED_LOCALE_KEY, preferences.getPreferredLocale().getValue());
    return values;
  }
}
