# Installation

The Mirror Node can be run [locally](#running-locally) or via [Docker](#running-via-docker-compose).

## Building

To run locally, first build the project using Java. Ensure you have Java 25 installed, then run the following command
from the top level directory. This will compile a runnable mirror node JAR file in the `target` directory.

```console
./gradlew clean build -x test
```

## Running Locally

### Database Setup

In addition to Java, you will need to install a [PostgreSQL](https://postgresql.org) database and initialize it.

Since [Flyway](https://flywaydb.org) will manage the database schema, the only required step is to run the database
initialization script. Locate the SQL script at `importer/src/main/resources/db/scripts/init.sh` and edit
the file to change the name and password variables at the top of the file to the desired values. Then make sure the
application [configuration](configuration.md)
matches the values in the script. Run the SQL script as a superuser and check the output carefully to ensure no errors
occurred.

```console
./importer/src/main/resources/db/scripts/init.sh
```

## Running via Gradle

Every component can be run via the Gradle `run` task. For example, to run the importer module run the following:

```
./gradlew :importer:run
```

## Running via Docker Compose

Docker Compose scripts are provided and can run all the mirror node components. Containers use the following persistent
volumes:

- `./db` on your local machine maps to `/var/lib/postgresql/data` in the db container. This contains the files for the
  PostgreSQL database. If the database container fails to initialise properly and the database fails to run, you will
  have to delete this folder before attempting a restart, otherwise the database initialization scripts will not be
  run.

### Configuration

Before starting, [configure](configuration.md) the application by updating the inline configuration at
`configs.app-config.content` within the `docker-compose.yml` file with the desired custom values. This file is passed to
Docker Compose and allows customized configuration for each of the mirror node components. The `application.yml` file
contents represent the minimal set of fields required to configure requester pays and must be uncommented and filled in.

### Starting

Finally, run the commands to build and startup:

```console
docker compose up
```

### Verifying

When running the mirror node using Docker, activity logs and container status for each module container can be viewed in
the [Docker Desktop Dashboard](https://docs.docker.com/desktop/dashboard/) or the `docker` CLI to verify expected
operation. You can also interact with mirror node APIs to verify their operation.

First list running docker container information using and verify an `Up` status is present:

```shell
$ docker ps
CONTAINER ID    IMAGE                                      COMMAND                 CREATED         STATUS          PORTS                   NAMES
21fa2a986d99    gcr.io/mirrornode/hedera-mirror-rest:main  "docker-entrypoint.s…"  7 minutes ago   Up 12 seconds   0.0.0.0:5551->5551/tcp  hedera-mirror-node_rest_1
56647c384d49    gcr.io/mirrornode/hedera-mirror-grpc:main  "java -cp /app/resou…"  8 minutes ago   Up 16 seconds   0.0.0.0:5600->5600/tcp  hedera-mirror-node_grpc_1
```

Using the IP address and port, the APIs' endpoints can be called to confirm data is processed and available.

#### Database

The following log can be used to confirm the database is up and running:

```shell
database system is ready to accept connections
```

If you have PostgreSQL installed you can connect directly to your database using the following `psql` command and
default [configurations](/docs/configuration.md):

```shell
psql "dbname=mirror_node host=localhost user=mirror_node password=mirror_node_pass port=5432"
```

If `psql` is not available locally you can `docker exec -it <CONTAINER ID> bash` into the db container and run the same
command above.

#### Application

Application containers have a `HEALTHCHECK` command within their respective Dockerfile that will verify basic
application health. The `docker logs` command can also be used to inspect the logs of individual containers and verify
their health.

### Stopping

Shut down the containers via `docker compose down`.
