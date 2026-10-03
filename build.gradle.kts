plugins {
    java
    // CAM-30: coverage de tests. Viene con Gradle, no es una dependencia nueva.
    jacoco
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "org.fleetguard"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    runtimeOnly("org.postgresql:postgresql:42.7.4")

    // Login (CAM-43): solo el módulo de criptografía de Spring Security (BCrypt),
    // sin la cadena de filtros ni el resto del framework de autenticación.
    implementation("org.springframework.security:spring-security-crypto")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    mainClass.set("org.fleetguard.FleetGuardApplication")
}

tasks.withType<JavaCompile> {
    // Needed so Jackson can deserialize DTO records from their constructor
    // parameter names alone (no extra @JsonCreator boilerplate needed).
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// CAM-30 / CAM-78: coverage mínimo de líneas. `./gradlew build` (y el CI) falla si baja del
// 85%. Solo se excluye la clase de arranque (un main de una línea); excluir paquetes para
// inflar el número le quitaría sentido al piso.
val coverageExclusions = listOf("org/fleetguard/FleetGuardApplication*")

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }))
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
