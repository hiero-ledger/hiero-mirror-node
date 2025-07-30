// SPDX-License-Identifier: Apache-2.0

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName

plugins {
    id("java-conventions")
    id("org.jooq.jooq-codegen-gradle")
}

dependencies {
    val dependencyManagement = project.extensions.getByType(DependencyManagementExtension::class)
    val jooqVersion = dependencyManagement.getManagedVersionsForConfiguration(null)["org.jooq:jooq"]
    implementation("org.jooq:jooq-postgres-extensions:$jooqVersion")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    // add postgres extensions as jooq codegen dependency to support int8range
    jooqCodegen("org.jooq:jooq-postgres-extensions:$jooqVersion")
}

object Database {
    val name = "mirror_node"
    val password = "mirror_node_pass"
    val schema = "public"
    val username = "mirror_node"
    var url = "postgresql://localhost:5432/mirror_node"
}

val dbDir = "${rootDir.absolutePath}/importer/src/main/resources/db"
val jooqTargetDir = "build/generated-sources/jooq"

java.sourceSets["main"].java { srcDir(jooqTargetDir) }

jooq {
    configuration {
        generator {
            database {
                excludes =
                    """
                    account_balance_old
                    | flyway_schema_history
                    | transaction_hash_.*
                    | .*_p\d+_\d+
                """
                includes = ".*"
                inputSchema = Database.schema
                isIncludeRoutines = false
                isIncludeUDTs = false
                name = "org.jooq.meta.postgres.PostgresDatabase"
            }
            target {
                directory = jooqTargetDir
                packageName = "org.hiero.mirror.restjava.jooq.domain"
            }
        }
        jdbc {
            driver = "org.postgresql.Driver"
            password = Database.password
            user = Database.username
        }
    }
    delayedConfiguration { jdbc { url = Database.url } }
}

val postgresqlContainer =
    tasks.register("postgresqlContainer") {
        val initSh = "${dbDir}/scripts/init.sh"
        doLast {
            val initScript = Transferable.of(File(initSh).readBytes())
            var image =
                DockerImageName.parse("docker.io/library/postgres:16.9-alpine")
                    .asCompatibleSubstituteFor("postgres")
            val container =
                PostgreSQLContainer<Nothing>(image).apply {
                    withCopyToContainer(initScript, "/docker-entrypoint-initdb.d/init.sh")
                    withUsername("postgres")
                    start()
                }
            val port = container.getMappedPort(5432)
            Database.url = "jdbc:postgresql://${container.host}:$port/mirror_node"
        }
    }

tasks.compileJava { dependsOn(tasks.jooqCodegen) }

val flywayMigrate =
    tasks.register("flywayMigrate") {
        val config =
            mutableMapOf("flyway.password" to Database.password, "flyway.user" to Database.username)
        val migrationDir = "${dbDir}/migration"
        val locations =
            arrayOf("filesystem:${migrationDir}/v1", "filesystem:${migrationDir}/common")
        val placeholders =
            mapOf(
                "api-password" to "mirror_api_password",
                "api-user" to "mirror_api_user",
                "db-name" to Database.name,
                "db-user" to Database.username,
                "partitionStartDate" to "'1970-01-01'",
                "partitionTimeInterval" to "'100 years'",
                "schema" to Database.schema,
                "tempSchema" to "temporary",
                "topicRunningHashV2AddedTimestamp" to "0",
            )
        doLast {
            config["flyway.url"] = Database.url
            Flyway.configure()
                .configuration(config)
                .locations(*locations)
                .placeholders(placeholders)
                .load()
                .migrate()
        }
        dependsOn(postgresqlContainer)
    }

tasks.jooqCodegen { dependsOn(flywayMigrate) }
