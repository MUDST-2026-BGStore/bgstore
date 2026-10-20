package com.chanakanlabs.theparlor.catalog.internal.web;

import com.chanakanlabs.theparlor.catalog.internal.application.TaxonomyService;
import com.chanakanlabs.theparlor.contracts.api.TaxonomyApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaxonomyController implements TaxonomyApi {
  private final TaxonomyService taxonomy;

  public TaxonomyController(TaxonomyService taxonomy) {
    this.taxonomy = taxonomy;
  }

  @Override
  public Category createCategory(CreateCategoryRequest createCategoryRequest) {
    return taxonomy.createCategory(
        createCategoryRequest.getName(), createCategoryRequest.getNames());
  }

  @Override
  public Mechanic createMechanic(CreateMechanicRequest createMechanicRequest) {
    return taxonomy.createMechanic(
        createMechanicRequest.getName(), createMechanicRequest.getNames());
  }

  @Override
  public Category getCategory(Long categoryId) {
    return taxonomy.category(categoryId);
  }

  @Override
  public Mechanic getMechanic(Long mechanicId) {
    return taxonomy.mechanic(mechanicId);
  }

  @Override
  public CategoryPage listCategories(Integer limit, @Nullable String cursor) {
    return taxonomy.categories(limit, cursor);
  }

  @Override
  public MechanicPage listMechanics(Integer limit, @Nullable String cursor) {
    return taxonomy.mechanics(limit, cursor);
  }

  @Override
  public Category updateCategory(Long categoryId, UpdateCategoryRequest updateCategoryRequest) {
    return taxonomy.updateCategory(categoryId, updateCategoryRequest.getNames());
  }

  @Override
  public Mechanic updateMechanic(Long mechanicId, UpdateMechanicRequest updateMechanicRequest) {
    return taxonomy.updateMechanic(mechanicId, updateMechanicRequest.getNames());
  }
}
