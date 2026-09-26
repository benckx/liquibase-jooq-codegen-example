import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.h2.Driver
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Version catalogs are not accessible in the buildscript block, so these
        // classpath versions must be kept in sync with gradle/libs.versions.toml.
        classpath("org.jooq:jooq-codegen:3.20.3")
        classpath("com.h2database:h2:2.5.250")
        classpath("org.liquibase:liquibase-core:4.31.1")
    }
}

plugins {
    alias(libs.plugins.versions)
    alias(libs.plugins.kotlin.jvm)
    java
    idea
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.liquibase.core)
    implementation(libs.sqlite.jdbc)
    implementation(libs.jooq)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("jooq"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
    minHeapSize = "1G"
    maxHeapSize = "1G"
}

tasks.register("dao-code-gen") {
    doLast {
        val conn = Driver().connect("jdbc:h2:mem:test", null)

        conn.createStatement().use { stmt ->
            stmt.execute("drop all OBJECTS")
            stmt.execute("create schema EXAMPLE_DB")
            stmt.execute("set schema EXAMPLE_DB")
        }

        val resourceAccessor = DirectoryResourceAccessor(file("src/main/resources"))
        val db = H2Database()
        db.connection = JdbcConnection(conn)

        val liquibase = Liquibase("liquibase-changelog.xml", resourceAccessor, db)
        liquibase.update(Contexts())
        conn.commit()

        GenerationTool.generate(
            Configuration()
                .withJdbc(
                    Jdbc()
                        .withDriver("org.h2.Driver")
                        .withUrl("jdbc:h2:mem:test")
                        .withUser("")
                        .withPassword("")
                )
                .withGenerator(
                    Generator()
                        .withDatabase(
                            Database()
                                .withExcludes("DATABASECHANGELOG|DATABASECHANGELOGLOCK")
                                .withInputSchema("EXAMPLE_DB")
                        )
                        .withGenerate(
                            Generate()
                                .withPojos(true)
                                .withDaos(true)
                        )
                        .withTarget(
                            Target()
                                .withPackageName("dev.encelade.example.dao.codegen")
                                .withDirectory(layout.buildDirectory.dir("jooq").get().asFile.absolutePath)
                        )
                )
        )

        conn.close()
    }
}

tasks.named("compileKotlin") {
    dependsOn("dao-code-gen")
}
