package com.chanakanlabs.theparlor.account.internal.domain;

import static com.chanakanlabs.theparlor.account.internal.domain.AccountPreferencePolicy.*;

import com.chanakanlabs.theparlor.shared.ContactStorageLimits;
import com.chanakanlabs.theparlor.shared.JsonValues;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@DynamicUpdate
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "phone_number", nullable = false, length = ContactStorageLimits.MAX_PHONE_LENGTH)
  private String phoneNumber;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  @Getter(AccessLevel.NONE)
  private Map<String, Object> preferences = new HashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Create a new account
  public static Account create(
      String firstName, String lastName, String phoneNumber, Map<String, Object> preferences) {
    var account = new Account();
    account.firstName = firstName;
    account.lastName = lastName;
    account.phoneNumber = phoneNumber;
    account.updatePreferences(preferences);
    return account;
  }

  public Map<String, Object> getPreferences() {
    return JsonValues.immutableCopy(preferences);
  }

  public void updateProfile(String firstName, String lastName, String phoneNumber) {
    if (firstName != null) this.firstName = firstName;
    if (lastName != null) this.lastName = lastName;
    if (phoneNumber != null) this.phoneNumber = phoneNumber;
  }

  public void updatePreferences(Map<String, Object> preferences) {
    var updated = new HashMap<>(this.preferences);
    updated.putAll(preferences);
    Object locale = updated.get(PREFERRED_LOCALE_KEY);
    if (!(locale instanceof String tag) || !SUPPORTED_LOCALES.contains(tag)) {
      throw new IllegalArgumentException("Supply a supported preferences." + PREFERRED_LOCALE_KEY);
    }
    this.preferences = JsonValues.immutableCopy(updated);
  }
}
