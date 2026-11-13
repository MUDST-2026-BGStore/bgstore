package com.chanakanlabs.theparlor.catalog.internal;

import com.chanakanlabs.theparlor.shared.KeysetCursor;

/**
 * Shared catalog limits for validation, diagnostics and ORM mappings.
 *
 * <p>Keep OpenAPI constraints aligned when changing a value. Storage changes require a new
 * migration. These policies remain internal to the catalog module.
 */
public final class CatalogLimits {
  private CatalogLimits() {}

  /** Maximum distinct category links per game; mechanics are a separate policy. */
  public static final int MAX_CATEGORIES = 12;

  /** Maximum distinct mechanic links per game; zero mechanics remain valid. */
  public static final int MAX_MECHANICS = 12;

  /** Maximum game title length in Unicode code points. */
  public static final int MAX_GAME_NAME_LENGTH = 200;

  /** Maximum optional designer credit length in Unicode code points. */
  public static final int MAX_DESIGNER_LENGTH = 200;

  /** Maximum immutable category/mechanic key length, also used by search filters. */
  public static final int MAX_TAXONOMY_KEY_LENGTH = 100;

  /** Maximum translated category/mechanic label length in Unicode code points. */
  public static final int MAX_LOCALIZED_NAME_LENGTH = 150;

  /** Maximum BCP 47 tag length before and after canonicalization; matches storage. */
  public static final int MAX_LOCALE_TAG_LENGTH = 35;

  /** Maximum description length per locale in Unicode code points. */
  public static final int MAX_DESCRIPTION_LENGTH = 4000;

  /** Maximum image URL length in Unicode code points, including input prevalidation. */
  public static final int MAX_IMAGE_URL_LENGTH = 2000;

  /** Maximum optional image alternative text length in Unicode code points. */
  public static final int MAX_IMAGE_ALT_TEXT_LENGTH = 200;

  /** Maximum search query length; independent of the game title length. */
  public static final int MAX_SEARCH_TEXT_LENGTH = 200;

  /** Maximum supplied values per taxonomy filter, counted before deduplication. */
  public static final int MAX_FILTER_VALUES = 10;

  /** Page size when the request omits limit. */
  public static final int DEFAULT_PAGE_SIZE = KeysetCursor.DEFAULT_PAGE_SIZE;

  /** Maximum requested catalog page size. */
  public static final int MAX_PAGE_SIZE = KeysetCursor.MAX_PAGE_SIZE;

  /** Maximum encoded cursor length in Java characters. */
  public static final int MAX_CURSOR_LENGTH = KeysetCursor.MAX_CURSOR_LENGTH;

  /** Hibernate collection fetch batch size; performance tuning only, not an API limit. */
  public static final int COLLECTION_BATCH_SIZE = 32;

  /** Canonical locale required in game descriptions and taxonomy display names. */
  public static final String REQUIRED_LOCALE = "en-US";
}
