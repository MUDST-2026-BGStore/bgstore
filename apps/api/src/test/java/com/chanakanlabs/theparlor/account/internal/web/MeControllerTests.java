package com.chanakanlabs.theparlor.account.internal.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.chanakanlabs.theparlor.account.AccountProvisioning;
import com.chanakanlabs.theparlor.account.internal.application.AccountService;
import com.chanakanlabs.theparlor.contracts.model.AccountPreferences;
import com.chanakanlabs.theparlor.contracts.model.AccountSettings;
import com.chanakanlabs.theparlor.contracts.model.PreferredLocale;
import com.chanakanlabs.theparlor.contracts.model.Profile;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class MeControllerTests {
  @ParameterizedTest
  @ValueSource(strings = {"+66898765432", "+442079460018"})
  void acceptsPhoneOnlyUpdates(String phone) throws Exception {
    var service = mock(AccountService.class);
    var mvc = standaloneSetup(new MeController(service, mock(AccountProvisioning.class))).build();
    when(service.updateCurrentProfile(null, null, phone))
        .thenReturn(
            new Profile(
                UUID.randomUUID(),
                "Test",
                "User",
                phone,
                new AccountPreferences(PreferredLocale.EN_US)));
    mvc.perform(
            patch("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + phone + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phoneNumber").value(phone))
        .andExpect(jsonPath("$.preferences.preferredLocale").value("en-US"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "0812345678", "+012345678", "+1234567890123456", "not a number"})
  void rejectsInvalidPhoneUpdates(String phone) throws Exception {
    var service = mock(AccountService.class);
    var mvc = standaloneSetup(new MeController(service, mock(AccountProvisioning.class))).build();
    mvc.perform(
            patch("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + phone + "\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(strings = {"en-US", "th-TH"})
  void acceptsSupportedLocaleUpdates(String locale) throws Exception {
    var service = mock(AccountService.class);
    var mvc = standaloneSetup(new MeController(service, mock(AccountProvisioning.class))).build();
    when(service.updateCurrentSettings(java.util.Map.of("preferredLocale", locale)))
        .thenReturn(new AccountSettings(new AccountPreferences(PreferredLocale.fromValue(locale))));

    mvc.perform(
            patch("/api/v1/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferences\":{\"preferredLocale\":\"" + locale + "\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences.preferredLocale").value(locale));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"preferences\":null}",
        "{\"preferences\":{}}",
        "{\"preferences\":{\"preferredLocale\":null}}",
        "{\"preferences\":{\"preferredLocale\":\"en-TH\"}}",
        "{\"preferredLocale\":\"th-TH\"}"
      })
  void rejectsMissingOrUnsupportedLocalesBeforeCallingTheService(String body) throws Exception {
    var service = mock(AccountService.class);
    var mvc = standaloneSetup(new MeController(service, mock(AccountProvisioning.class))).build();

    mvc.perform(patch("/api/v1/me/settings").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"firstName\":null}",
        "{\"firstName\":\"\"}",
        "{\"lastName\":\"   \"}",
        "{\"preferredLocale\":\"th-TH\"}"
      })
  void rejectsEmptyOrInvalidProfileUpdates(String body) throws Exception {
    var service = mock(AccountService.class);
    var mvc = standaloneSetup(new MeController(service, mock(AccountProvisioning.class))).build();
    mvc.perform(patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void acceptsANameOnlyUpdate() throws Exception {
    var service = mock(AccountService.class);
    var mvc = standaloneSetup(new MeController(service, mock(AccountProvisioning.class))).build();
    when(service.updateCurrentProfile("Alice", null, null))
        .thenReturn(
            new Profile(
                UUID.randomUUID(),
                "Alice",
                "User",
                "+66812345678",
                new AccountPreferences(PreferredLocale.EN_US)));
    mvc.perform(
            patch("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Alice\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Alice"))
        .andExpect(jsonPath("$.lastName").value("User"));
  }

  @Test
  void onboardingPassesNestedPreferencesToProvisioning() throws Exception {
    var service = mock(AccountService.class);
    var provisioning = mock(AccountProvisioning.class);
    var mvc = standaloneSetup(new MeController(service, provisioning)).build();
    var profile =
        new Profile(
            UUID.randomUUID(),
            "First",
            "Last",
            "+66812345678",
            new AccountPreferences(PreferredLocale.EN_US).putAdditionalProperty("theme", "dark"));
    when(service.findCurrentProfile()).thenReturn(java.util.Optional.of(profile));

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {"firstName":"First","lastName":"Last","phoneNumber":"+66812345678",
             "preferences":{"preferredLocale":"en-US","theme":"dark"}}
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences.preferredLocale").value("en-US"))
        .andExpect(jsonPath("$.preferences.theme").value("dark"))
        .andExpect(jsonPath("$.preferredLocale").doesNotExist());
    org.mockito.Mockito.verify(provisioning)
        .provisionForCurrentIdentity(
            new com.chanakanlabs.theparlor.account.AccountRegistration(
                "First",
                "Last",
                "+66812345678",
                java.util.Map.of("preferredLocale", "en-US", "theme", "dark")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        ",\"preferredLocale\":\"en-US\"",
        ",\"preferences\":{}",
        ",\"preferences\":{\"preferredLocale\":null}",
        ",\"preferences\":{\"preferredLocale\":\"en-TH\"}"
      })
  void onboardingRequiresASupportedNestedLocale(String preferences) throws Exception {
    var service = mock(AccountService.class);
    var provisioning = mock(AccountProvisioning.class);
    var mvc = standaloneSetup(new MeController(service, provisioning)).build();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"firstName\":\"First\",\"lastName\":\"Last\",\"phoneNumber\":\"+66812345678\""
                        + preferences
                        + "}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service, provisioning);
  }

  @Test
  void readsCurrentSettings() throws Exception {
    var service = mock(AccountService.class);
    var mvc = standaloneSetup(new MeController(service, mock(AccountProvisioning.class))).build();
    when(service.getCurrentSettings())
        .thenReturn(new AccountSettings(new AccountPreferences(PreferredLocale.TH_TH)));
    mvc.perform(get("/api/v1/me/settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences.preferredLocale").value("th-TH"));
  }
}
