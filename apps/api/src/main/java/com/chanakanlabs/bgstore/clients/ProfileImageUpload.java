package com.chanakanlabs.bgstore.clients;

/** Profile-image content that a future account API can validate before database storage. */
public final class ProfileImageUpload {

  private final String mediaType;
  private final String filename;
  private final byte[] content;

  public ProfileImageUpload(String mediaType, String filename, byte[] content) {
    this.mediaType = mediaType;
    this.filename = filename;
    this.content = content.clone();
  }

  public String mediaType() {
    return mediaType;
  }

  public String filename() {
    return filename;
  }

  public byte[] content() {
    return content.clone();
  }
}
