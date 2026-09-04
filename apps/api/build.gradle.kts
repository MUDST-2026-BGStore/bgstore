import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("dev.nx.gradle.project-graph") version ("0.1.24")
  java
  jacoco
  id("com.diffplug.spotless") version "8.10.0"
  id("org.openapi.generator") version "7.24.0"
  id("org.springframework.boot") version "4.1.1"
  id("io.spring.dependency-management") version "1.1.7"
  id("net.ltgt.errorprone") version "5.1.0"
  id("org.jooq.jooq-codegen-gradle") version "3.21.7"
}

group = "com.chanakanlabs.bgstore"

version = "0.0.1-SNAPSHOT"

description = "BGStore backend API"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

val applicationMainClass = "com.chanakanlabs.bgstore.BgstoreApiApplication"

springBoot {
  mainClass = applicationMainClass
}

// Nx schedules resolveMainClassName as its own task, so bootRun and bootJar
// cannot read that task output. Setting the main class directly bypasses the
// resolver.
tasks.bootRun {
  mainClass = applicationMainClass
}

tasks.bootJar {
  mainClass = applicationMainClass
}

repositories {
  mavenCentral()
}

extra["springModulithVersion"] = "2.1.0"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-jooq")
  implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.flywaydb:flyway-database-postgresql")
  implementation("org.springframework.modulith:spring-modulith-starter-core")
  implementation("org.springframework.modulith:spring-modulith-starter-insight")
  jooqCodegen("org.jooq:jooq-meta-extensions:3.21.7")
  errorprone("com.google.errorprone:error_prone_core:2.50.0")
  errorprone("com.uber.nullaway:nullaway:0.13.8")
  runtimeOnly("io.micrometer:micrometer-registry-prometheus")
  runtimeOnly("org.postgresql:postgresql")
  runtimeOnly("org.springframework.modulith:spring-modulith-runtime")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-test")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.springframework.modulith:spring-modulith-starter-test")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
  imports {
    mavenBom(
        "org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}"
    )
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

val generatedOpenApiDirectory = layout.buildDirectory.dir("generated/openapi")

// CONTRIBUTING.md requires jOOQ types generated from the migrated schema. The
// generator reads the Flyway scripts directly, so neither the build nor CI
// needs a database to produce them.
val generatedJooqDirectory = layout.buildDirectory.dir("generated/jooq")

openApiGenerate {
  generatorName.set("spring")
  inputSpec.set(file("../../packages/contracts/openapi.yaml").absolutePath)
  outputDir.set(generatedOpenApiDirectory.get().asFile.absolutePath)
  apiPackage.set("com.chanakanlabs.bgstore.contract.api")
  modelPackage.set("com.chanakanlabs.bgstore.contract.model")
  globalProperties.set(
      mapOf(
          "apis" to "",
          "models" to "",
          "supportingFiles" to "false",
      )
  )
  configOptions.set(
      mapOf(
          "annotationLibrary" to "none",
          "documentationProvider" to "none",
          "interfaceOnly" to "true",
          "openApiNullable" to "false",
          "skipDefaultInterface" to "true",
          "useJakartaEe" to "true",
          "useSpringBoot3" to "true",
          "useTags" to "true",
      )
  )
  validateSpec.set(true)
}

openApiValidate {
  inputSpec.set(file("../../packages/contracts/openapi.yaml").absolutePath)
}

sourceSets {
  main {
    java.srcDir(generatedOpenApiDirectory.map { it.dir("src/main/java") })
    java.srcDir(generatedJooqDirectory)
  }
}

tasks.compileJava {
  dependsOn(tasks.openApiGenerate, tasks.jooqCodegen)
  options.errorprone {
    disableWarningsInGeneratedCode.set(true)
    excludedPaths.set(".*/build/generated/.*")
    error("NullAway")
    option("NullAway:AnnotatedPackages", "com.chanakanlabs.bgstore")
  }
}

tasks.compileTestJava {
  options.errorprone.disable("NullAway")
}

spotless {
  java {
    target("src/**/*.java")
    googleJavaFormat()
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

jacoco {
  toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  classDirectories.setFrom(
      sourceSets.main.get().output.asFileTree.matching {
        exclude(
            "**/contract/**",
            "**/database/**",
            "**/BgstoreApiApplication.class",
            "**/security/**",
        )
      }
  )
  reports {
    xml.required.set(true)
    html.required.set(true)
  }
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.test)
  classDirectories.setFrom(
      sourceSets.main.get().output.asFileTree.matching {
        exclude(
            "**/contract/**",
            "**/database/**",
            "**/BgstoreApiApplication.class",
            "**/security/**",
        )
      }
  )
  violationRules {
    rule {
      limit {
        counter = "LINE"
        minimum = "0.80".toBigDecimal()
      }
      limit {
        counter = "BRANCH"
        minimum = "0.75".toBigDecimal()
      }
    }
  }
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}

allprojects {
  apply {
    plugin("dev.nx.gradle.project-graph")
  }
}

jooq {
  configuration {
    generator {
      database {
        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
        properties {
          property {
            key = "scripts"
            // Identity tables are accessed through the application-owned JDBC
            // repositories; only catalogue tables need generated jOOQ types.
            // Keeping this scoped also avoids asking jOOQ's DDL parser to
            // interpret provider-specific identity constraints, which it cannot
            // simulate.
            //
            // jOOQ takes one Ant-style pattern rather than a list, so every
            // migration that shapes a catalogue table is named `V*__games*.sql`
            // to be matched here; `sort` below replays them in Flyway order.
            value = "src/main/resources/db/migration/V*__games*.sql"
          }
          property {
            key = "sort"
            value = "flyway"
          }
          property {
            key = "defaultNameCase"
            value = "lower"
          }
          property {
            key = "parseDialect"
            value = "POSTGRES"
          }
          property {
            key = "parseIgnoreComments"
            value = "true"
          }
        }
      }
      target {
        packageName = "com.chanakanlabs.bgstore.database"
        directory = generatedJooqDirectory.get().asFile.absolutePath
      }
    }
  }
}
