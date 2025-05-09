// SPDX-License-Identifier: Apache-2.0

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.images.builder.Transferable

plugins {
    id("java-conventions")
    id("org.flywaydb.flyway")
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

val dbContainerProvider =
    project.gradle.sharedServices.registerIfAbsent("postgres", PostgresService::class.java) {
        parameters.getRootDir().set(rootDir.absolutePath)
    }
val dbName = "mirror_node"
val dbPassword = "mirror_node_pass"
val dbSchema = "public"
val dbUser = "mirror_node"
val jooqTargetDir = "build/generated-sources/jooq"

fun getJdbcUrl(container: PostgreSQLContainer<Nothing>): String {
    val port = container.getMappedPort(5432).toString()
    return "jdbc:postgresql://" + container.host + ":" + port + "/" + dbName
}

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
                inputSchema = dbSchema
                isIncludeRoutines = false
                isIncludeUDTs = false
                name = "org.jooq.meta.postgres.PostgresDatabase"
            }
            target {
                directory = jooqTargetDir
                packageName = "org.hiero.mirror.restjava.jooq.domain"
            }
        }
    }
}

tasks.compileJava { dependsOn(tasks.jooqCodegen) }

tasks.flywayMigrate {
    locations =
        arrayOf(
            "filesystem:../importer/src/main/resources/db/migration/v1",
            "filesystem:../importer/src/main/resources/db/migration/common",
        )
    password = dbPassword
    placeholders =
        mapOf(
            "api-password" to "mirror_api_password",
            "api-user" to "mirror_api_user",
            "db-name" to dbName,
            "db-user" to dbUser,
            "partitionStartDate" to "'1970-01-01'",
            "partitionTimeInterval" to "'100 years'",
            "schema" to dbSchema,
            "tempSchema" to "temporary",
            "topicRunningHashV2AddedTimestamp" to "0",
        )
    user = dbUser

    usesService(dbContainerProvider)

    doFirst { url = getJdbcUrl(dbContainerProvider.get().getContainer()) }
}

tasks.jooqCodegen {
    dependsOn(tasks.flywayMigrate)
    usesService(dbContainerProvider)

    doFirst {
        jooq {
            configuration {
                jdbc {
                    driver = "org.postgresql.Driver"
                    password = dbPassword
                    url = getJdbcUrl(dbContainerProvider.get().getContainer())
                    user = dbUser
                }
            }
        }
    }
}

/** Build service for providing database container. */
abstract class PostgresService : BuildService<PostgresService.Params>, AutoCloseable {
    interface Params : BuildServiceParameters {
        fun getRootDir(): Property<String>
    }

    private var container: PostgreSQLContainer<Nothing>

    init {
        val initScript =
            Transferable.of(
                File(
                        parameters.getRootDir().get() +
                            "/importer/src/main/resources/db/scripts/init.sh"
                    )
                    .readBytes()
            )
        container =
            PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
                withCopyToContainer(initScript, "/docker-entrypoint-initdb.d/init.sh")
                withUsername("postgres")
                start()
            }
    }

    override fun close() {
        container.stop()
    }

    fun getContainer(): PostgreSQLContainer<Nothing> {
        return container
    }
}
