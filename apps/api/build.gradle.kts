plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "7.0.4"
    id("org.openapi.generator") version "7.26.0"
}

group = "com.chanakanlabs"
version = "0.0.1-SNAPSHOT"
description = "The Parlor backend API"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
    imports { mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.1") }
}

dependencies {
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.mapstruct:mapstruct:1.6.3")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
  annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
  annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("com.github.dasniko:testcontainers-keycloak:4.4.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

spotless {
	java {
		googleJavaFormat()
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}


val workspaceDirectory = rootDir.resolve("../..")
val isWindows = org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS)
val suppliedContract = providers.gradleProperty("openApiSpec")
val bundledContract = workspaceDirectory.resolve("dist/openapi/theparlor.openapi.yaml")
val contract = suppliedContract.getOrElse(bundledContract.absolutePath)
val bundleOpenApi = tasks.register<Exec>("bundleOpenApi") {
    description = "Bundle the split OpenAPI sources for validation and code generation"
    workingDir(workspaceDirectory)
    // Let the shell resolve pnpm from this invocation's PATH, including with an existing daemon.
    if (isWindows) commandLine("cmd", "/c", "pnpm bundle:contract") else commandLine("sh", "-c", "pnpm bundle:contract")
    inputs.dir(workspaceDirectory.resolve("openapi")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files("../../redocly.yaml", "../../.redocly.lint-ignore.yaml", "../../package.json", "../../pnpm-lock.yaml")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(bundledContract)
}
openApiValidate { inputSpec.set(contract) }
tasks.named("openApiValidate") {
    if (!suppliedContract.isPresent) dependsOn(bundleOpenApi)
}
openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(contract)
    outputDir.set(layout.buildDirectory.dir("generated/openapi"))
    apiPackage.set("com.chanakanlabs.theparlor.contracts.api")
    modelPackage.set("com.chanakanlabs.theparlor.contracts.model")
    configOptions.set(mapOf(
        "interfaceOnly" to "true", "skipDefaultInterface" to "true",
        "useTags" to "true", "useSpringBoot4" to "true", "useJackson3" to "true",
        "useResponseEntity" to "false", "annotationLibrary" to "none",
        "documentationProvider" to "none", "useSwaggerUI" to "false",
        // Spring Security validates CSRF before dispatch; controllers do not consume this header.
        "implicitHeadersRegex" to "^X-CSRF-TOKEN$",
        "openApiNullable" to "false", "containerDefaultToNull" to "true",
        "generateJsonIncludeAnnotations" to "true", "hideGenerationTimestamp" to "true"
    ))
}
sourceSets.main { java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java")) }
tasks.compileJava { dependsOn(tasks.openApiGenerate) }
tasks.register<Exec>("generateWebClient") {
    if (!suppliedContract.isPresent) dependsOn(bundleOpenApi)
    description = "Generate API operations and Vue Query composables with Orval"
    workingDir(workspaceDirectory)
    if (isWindows) commandLine("cmd", "/c", "pnpm exec orval") else commandLine("pnpm", "exec", "orval")
    environment("THEPARLOR_OPENAPI_SPEC", contract)
    inputs.file(contract)
    inputs.files("../../orval.config.ts", "../../package.json", "../../pnpm-lock.yaml",
        "../../libs/web/api-client/src/lib/transport.ts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(workspaceDirectory.resolve("libs/web/api-client/src/generated"))
}
spotless { java { target("src/**/*.java") } }
tasks.withType<Test> {
    testLogging { events("passed", "skipped", "failed") }
    if (providers.gradleProperty("skipIntegration").isPresent) {
        useJUnitPlatform { excludeTags("integration") }
    }
}

tasks.processTestResources {
    from(rootDir.resolve("../../deploy/keycloak/realm-theparlor.json"))
}

// Prevent renamed generated packages from surviving a contract/package migration.
tasks.named("openApiGenerate") {
    if (!suppliedContract.isPresent) dependsOn(bundleOpenApi)
    doFirst { delete(layout.buildDirectory.dir("generated/openapi")) }
}

// Keep destructive local tooling out of the application jar and its module graph.
val maintenance = sourceSets.create("maintenance") {
    compileClasspath += configurations.runtimeClasspath.get()
    runtimeClasspath += configurations.runtimeClasspath.get()
}
val maintenanceTest = sourceSets.create("maintenanceTest") {
    resources.srcDir("../../deploy/keycloak")
    compileClasspath += maintenance.output + configurations.testCompileClasspath.get()
    runtimeClasspath += maintenance.output + configurations.testRuntimeClasspath.get()
}
tasks.register<JavaExec>("manageUsers") {
    description = "Delete local users and their PostgreSQL/Valkey resources (use the Makefile wrappers)"
    classpath = maintenance.runtimeClasspath
    mainClass.set("com.chanakanlabs.theparlor.tools.UserMaintenance")
}
tasks.register<JavaExec>("seedReferenceData") {
    description = "Reset and reseed local branches, catalog, editions, stock and tables from seed/reference-data.json (use make seed-dev)"
    classpath = maintenance.runtimeClasspath
    mainClass.set("com.chanakanlabs.theparlor.tools.ReferenceDataSeed")
}
tasks.register<Test>("maintenanceTest") {
    description = "Verify local user cleanup against isolated infrastructure"
    testClassesDirs = maintenanceTest.output.classesDirs
    classpath = maintenanceTest.runtimeClasspath
}
tasks.check { dependsOn("maintenanceTest") }

// Documentation generation is opt-in and does not count as a passing test.
tasks.register<JavaExec>("generateModuleDocumentation") {
    description = "Generate Spring Modulith diagrams and module canvases"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.chanakanlabs.theparlor.ModuleDocumentation")
}
