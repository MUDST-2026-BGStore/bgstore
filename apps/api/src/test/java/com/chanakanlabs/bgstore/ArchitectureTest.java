package com.chanakanlabs.bgstore;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

final class ArchitectureTest {

  private static final String APPLICATION_PACKAGE = "com.chanakanlabs.bgstore..";
  private static final JavaClasses APPLICATION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(APPLICATION_PACKAGE);

  @Test
  void modularMonolithBoundariesAreValid() {
    ApplicationModules.of(BgstoreApiApplication.class).verify();
  }

  @Test
  void applicationComponentsUseConstructorInjection() {
    noFields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAnyPackage(APPLICATION_PACKAGE)
        .should()
        .beAnnotatedWith(Autowired.class)
        .check(APPLICATION_CLASSES);
  }

  @Test
  void controllersDoNotReachPersistenceAdaptersOrEntities() {
    noClasses()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("JpaRepository")
        .check(APPLICATION_CLASSES);

    noClasses()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .dependOnClassesThat()
        .areAnnotatedWith(Entity.class)
        .check(APPLICATION_CLASSES);
  }

  @Test
  void servicesDoNotDeclareMvcConcerns() {
    noClasses()
        .that()
        .areAnnotatedWith(Service.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.web.bind.annotation..", "org.springframework.web.servlet..")
        .check(APPLICATION_CLASSES);
  }

  @Test
  void controllersDoNotOwnTransactions() {
    noClasses()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .beAnnotatedWith(Transactional.class)
        .check(APPLICATION_CLASSES);
  }
}
