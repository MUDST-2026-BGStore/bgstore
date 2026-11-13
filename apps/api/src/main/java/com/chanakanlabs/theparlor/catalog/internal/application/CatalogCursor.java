package com.chanakanlabs.theparlor.catalog.internal.application;

import com.chanakanlabs.theparlor.shared.KeysetCursor;
import java.util.ArrayList;

/** Catalog filter encoding on top of the shared operation/filter-bound keyset cursors. */
final class CatalogCursor {
  private CatalogCursor() {}

  static int limit(Integer limit) {
    return KeysetCursor.limit(limit);
  }

  static String after(String cursor, String operation, String filters) {
    return KeysetCursor.after(cursor, operation, filters);
  }

  static String next(String operation, String filters, Object id) {
    return KeysetCursor.next(operation, filters, id);
  }

  static String gameFilters(GameSearchCriteria filters) {
    var fields = new ArrayList<String>();
    fields.add(filters.q());
    fields.add(Integer.toString(filters.categories().size()));
    fields.addAll(filters.categories());
    fields.add(Integer.toString(filters.mechanics().size()));
    fields.addAll(filters.mechanics());
    fields.add(filters.difficulty() == null ? null : filters.difficulty().name());
    fields.add(filters.players() == null ? null : filters.players().toString());
    var value = new StringBuilder();
    for (String field : fields) value.append(field == null ? "-1:" : field.length() + ":" + field);
    return value.toString();
  }
}
