package com.chanakanlabs.bgstore.clients;

/** Profile-image content read from the BGStore database. */
final class ClientProfileImageData {

  private final String mediaType;
  private final String filename;
  private final byte[] content;

  ClientProfileImageData(String mediaType, String filename, byte[] content) {
    this.mediaType = mediaType;
    this.filename = filename;
    this.content = content.clone();
  }

  String mediaType() {
    return mediaType;
  }

  String filename() {
    return filename;
  }

  byte[] content() {
    return content.clone();
  }
}
