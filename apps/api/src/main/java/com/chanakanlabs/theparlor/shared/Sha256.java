package com.chanakanlabs.theparlor.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 digests for storing bearer secrets and request fingerprints, never the values. */
public final class Sha256 {
  private Sha256() {}

  public static byte[] of(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }

  /** Constant-time comparison, so response timing reveals nothing about a stored digest. */
  public static boolean matches(byte[] storedDigest, String candidate) {
    return candidate != null
        && storedDigest != null
        && MessageDigest.isEqual(storedDigest, of(candidate));
  }
}
