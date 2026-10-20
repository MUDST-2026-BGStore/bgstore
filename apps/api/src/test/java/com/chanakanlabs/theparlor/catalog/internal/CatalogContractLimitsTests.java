package com.chanakanlabs.theparlor.catalog.internal;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Fails when Java policy and the independently generated HTTP contract drift apart. */
class CatalogContractLimitsTests {
  @Test
  void gameSchemasMatchDomainLimits() throws IOException {
    for (String schema : List.of("Game", "CreateGameRequest", "UpdateGameRequest")) {
      var properties = child(read("components/schemas/" + schema + ".yaml"), "properties");
      limit(properties, "name", "maxLength", MAX_GAME_NAME_LENGTH);
      limit(properties, "designer", "maxLength", MAX_DESIGNER_LENGTH);
      limit(properties, "categories", "maxItems", MAX_CATEGORIES);
      limit(properties, "categories", "minItems", 1);
      limit(properties, "mechanics", "maxItems", MAX_MECHANICS);
      for (String field : List.of("categories", "mechanics")) {
        assertThat(child(child(properties, field), "items"))
            .containsEntry("maxLength", MAX_TAXONOMY_KEY_LENGTH);
      }
      assertThat(child(child(properties, "descriptions"), "additionalProperties"))
          .containsEntry("maxLength", MAX_DESCRIPTION_LENGTH);
    }
  }

  @Test
  void imageEditionAndTaxonomySchemasMatchDomainLimits() throws IOException {
    for (String schema : List.of("GameImage", "GameImageInput")) {
      var properties = child(read("components/schemas/" + schema + ".yaml"), "properties");
      limit(properties, "url", "maxLength", MAX_IMAGE_URL_LENGTH);
      limit(properties, "altText", "maxLength", MAX_IMAGE_ALT_TEXT_LENGTH);
    }
    for (String schema :
        List.of("GameEdition", "CreateGameEditionRequest", "UpdateGameEditionRequest")) {
      limit(
          child(read("components/schemas/" + schema + ".yaml"), "properties"),
          "languageCode",
          "maxLength",
          MAX_LOCALE_TAG_LENGTH);
    }
    for (String schema :
        List.of(
            "Category",
            "Mechanic",
            "CreateCategoryRequest",
            "CreateMechanicRequest",
            "UpdateCategoryRequest",
            "UpdateMechanicRequest")) {
      var properties = child(read("components/schemas/" + schema + ".yaml"), "properties");
      if (properties.containsKey("name"))
        limit(properties, "name", "maxLength", MAX_TAXONOMY_KEY_LENGTH);
      assertThat(child(child(properties, "names"), "additionalProperties"))
          .containsEntry("maxLength", MAX_LOCALIZED_NAME_LENGTH);
    }
  }

  @Test
  void paginationAndSearchMatchTheContract() throws IOException {
    assertThat(child(read("components/parameters/Limit.yaml"), "schema"))
        .containsEntry("default", DEFAULT_PAGE_SIZE)
        .containsEntry("maximum", MAX_PAGE_SIZE);
    assertThat(child(read("components/parameters/Cursor.yaml"), "schema"))
        .containsEntry("maxLength", MAX_CURSOR_LENGTH);
    var parameters = (List<?>) child(read("paths/games/collection.yaml"), "get").get("parameters");
    for (Object parameter : parameters) {
      var value = (Map<?, ?>) parameter;
      if ("q".equals(value.get("name"))) {
        assertThat(child(value, "schema")).containsEntry("maxLength", MAX_SEARCH_TEXT_LENGTH);
      }
      if ("categories".equals(value.get("name")) || "mechanics".equals(value.get("name"))) {
        var schema = child(value, "schema");
        assertThat(schema).containsEntry("maxItems", MAX_FILTER_VALUES);
        assertThat(child(schema, "items")).containsEntry("maxLength", MAX_TAXONOMY_KEY_LENGTH);
      }
    }
  }

  private static void limit(Map<?, ?> properties, String field, String constraint, int expected) {
    assertThat(child(properties, field))
        .as("%s.%s", field, constraint)
        .containsEntry(constraint, expected);
  }

  private static Map<String, Object> read(String relative) throws IOException {
    // Gradle test working directory is apps/api; read contract sources, never generated output.
    try (var input = Files.newInputStream(Path.of("../../openapi").resolve(relative))) {
      return new Yaml().load(input);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> child(Map<?, ?> value, String key) {
    assertThat(value.get(key)).as("Contract node %s", key).isInstanceOf(Map.class);
    return (Map<String, Object>) value.get(key);
  }
}
