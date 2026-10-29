---
title: Installation Guide
description: "Viglet Shio Installation Guide: Docker, JAR, build from source, database setup, and Linux service."
sidebar:
  order: 1
---

Viglet Shio ([https://www.viglet.org/shio/](https://www.viglet.org/shio/)) is an open-source, agent-ready CMS ([https://github.com/openviglet/shio](https://github.com/openviglet/shio)) that lets you model content, query it via GraphQL, and build websites using JavaScript with native caching and search.

## Installing Java

Shio requires Java.

:::note
Shio only supports Java 21.
:::

During installation, Shio reads the JAVA_HOME and PATH variables to install the necessary services. Therefore, you must modify the variables as specified here:

- **On Windows:**
  - Set the JAVA_HOME system variable to point to the JAVA location.
  - Attach the PATH system variable with `%JAVA_HOME%/bin`.

- **On Unix:**
  - Create an environment variable called JAVA_HOME.
  - Provide the JAVA bin path in the PATH variable.

<span id="shio-cms-requirements"></span>

## Shio Requirements

The information below describes the resource modes available to run Shio.

:::note
Use a screen resolution of at least 1024 x 768 for optimum clarity.
:::

### Hardware Requirements

| Minimum | Recommended |
|---------|-------------|
| Single 1.5 GHz processor, 1 GB available memory | Dual 2 GHz processors with Hyper-Threading enabled, 2 GB available memory |

In addition, the following is required:

- 1GB of disk space for software.
- A compatible version of a database.

## Installing and Configuring Databases

Shio supports multiple database platforms, each with specific unique requirements to maximize effectiveness with Shio.

<span id="shio-cms-database-table-requirements"></span>

### Shio Database Table Requirements

Shio uses multiple tables in multiple databases (also called database schemas by some vendors such as Oracle). You must create new databases / schemas that will contain the sets of tables described below.

### Oracle Database Requirements

This section describes configuration requirements for an existing database.

To set up an Oracle database:

1. Install and configure a single database instance for use by Shio.
2. Create a database schema to contain the Shio system tables.
3. Configure the Shio admin user for the database. For Oracle, the database user with the grant of the CONNECT and RESOURCE roles. Make sure the user has UNLIMITED TABLESPACE and CREATE VIEW privileges.

### Microsoft SQL Server Database Requirements

When creating the Shio database, use the following options:

- You must install the Database Engine feature and Management Tools are recommended.
- Set the Collation configuration variable to `SQL_Latin1_General_CP1_CS_AS` or `Latin1_General / Case-sensitive / Accent-Sensitive`.
- Set authentication mode to mixed mode.
- Shio system user cannot be sa.

#### Configuring the SQL Server Database and Schemas

:::note
The database and usernames provided are examples only.
:::

To create the SQL Server database:

1. Select Databases > New Database.
2. Enter the database name (eg shio) and the size of the data and log files according to the environment.
3. Go to the Options page and set the following values:
   - Collation = `Latin1_General_CS_AS` or `SQL_Latin1_General_CP1_CS_AS`
   - Miscellaneous > Allow Snapshot Isolation = True
   - Miscellaneous > Is Read Committed Snapshot On = True

```sql
USE master
GO
ALTER DATABASE shio
SET SINGLE_USER
WITH ROLLBACK IMMEDIATE
GO
ALTER DATABASE shio
SET ALLOW_SNAPSHOT_ISOLATION ON
GO
ALTER DATABASE shio
SET READ_COMMITTED_SNAPSHOT ON
GO
ALTER DATABASE shio
SET MULTI_USER
GO
```

4. Under Security > Logins, select New Login:
   - Enter the Login name (for example, shio)
   - Deselect the Enforce Password Expiration option
   - Set the Default database

5. On the User Mapping page:
   - Default user = shio
   - Default schema = shio
   - Select the option for db_owner

### PostgreSQL Database Requirements

#### Installing and configuring the PostgreSQL Database

To install the PostgreSQL database:

1. Navigate to [https://www.postgresql.org/docs/manuals/](https://www.postgresql.org/docs/manuals/) and follow the instructions.

To configure the PostgreSQL instance, open `postgresql.conf` and set:

```ini
max_connections = 10
max_prepared_transactions = 10
shared_buffers = 512MB
autovacuum = off
```

:::important
The autovacuum daemon must be re-enabled after installing and configuring Shio.
:::

#### Configuring the Shio user, database, and schemas

:::important
When you create a database table in PostgreSQL, the field name must be lowercase.
:::

1. Create a user:

```sql
CREATE USER shio PASSWORD <password> with createuser;
```

2. Create the database:

```sql
CREATE DATABASE shio_db;
ALTER DATABASE shio_db OWNER TO shio;
```

3. Log in and create the schema:

```bash
$psql -U shio -d shio_db
```

```sql
CREATE SCHEMA shio;
ALTER SCHEMA shio OWNER TO shio;
ALTER USER shio SET SEARCH_PATH = shio;
```

---

<span id="installing-shio-cms"></span>

## Installing Shio

### Option 1: Docker (fastest)

Pull the pre-built image published to the GitHub Container Registry (`ghcr.io`) by the **Publish Docker Image** GitHub Action:

```bash
docker pull ghcr.io/openviglet/shio-ce:latest
docker run -p 2710:2710 -e SHIO_ADMIN_PASSWORD=change-me-please ghcr.io/openviglet/shio-ce:latest
```

`SHIO_ADMIN_PASSWORD` (minimum six characters) gives the container a usable `admin` login on its
first start, instead of waiting for someone to open the console and set one. Leave it out and the
console asks on first access instead. See
[Setting the admin password without the console](#setting-the-admin-password-without-the-console).

The admin **Marketplace** reads the published package catalogue by default, so a fresh container
lists the showcase site. That is an outbound request to `openviglet.github.io`, and an install
writes the package under `store/blueprints`. On an air-gapped or multi-tenant instance add
`-e SHIO_MARKETPLACE_ENABLED=false` (see the [configuration reference](/shio/docs/configuration-reference/)).

:::tip[Image tags]
Besides `latest`, every run also publishes the project version and a commit tag (`sha-<short>`). Pin a specific tag in production instead of tracking `latest`.
:::

The image is tuned for one container per instance: it starts from a class data sharing
archive built into the image, and runs with `JAVA_OPTS="-XX:+UseSerialGC -Xms64m -Xmx512m"`.
For a larger deployment, set your own `JAVA_OPTS`, for example
`-e JAVA_OPTS="-XX:+UseG1GC -Xms1g -Xmx2g"`. The class data sharing archive is not part of
`JAVA_OPTS`, so overriding it keeps the faster start.

### Option 2: JAR download

Go to [https://www.viglet.org/shio/download/](https://www.viglet.org/shio/download/) and click on "Download Shio" button to download the `viglet-shio.jar` executable.

Copy the `viglet-shio.jar` to `/appl/viglet/shio/server`:

```shell
mkdir -p /appl/viglet/shio/server
cp viglet-shio.jar /appl/viglet/shio/server
```

### Option 3: Build from source

```bash
git clone https://github.com/openviglet/shio.git
cd shio
mvn clean package -pl shio-app
cp shio-app/target/viglet-shio.jar /appl/viglet/shio/server/
```

### Database

By default, Shio uses H2 as its embedded database, but if you prefer another database you can create an external properties file and reference it at startup using `--spring.config.additional-location`.

Create `/appl/viglet/shio/server/viglet-shio.properties`:

#### MySQL

```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/shio
spring.datasource.username=shio
spring.datasource.password=shio
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL5InnoDBDialect
```

#### Oracle Database

```properties
spring.datasource.url=jdbc:oracle:thin:@localhost:1521/shio
spring.datasource.username=shio
spring.datasource.password=shio
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.Oracle10gDialect
```

#### PostgreSQL

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/shio
spring.datasource.username=shio
spring.datasource.password=shio
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQL94Dialect
```

Then start Shio referencing the properties file:

```bash
java -Xmx1g -Xms1g -jar viglet-shio.jar \
  --spring.config.additional-location=file:/appl/viglet/shio/server/viglet-shio.properties
```

---

<span id="creating-shio-cms-service-on-linux"></span>

### Creating Shio Service on Linux

As root, create a `/etc/systemd/system/shio.service` file with the following lines:

```ini
[Unit]
Description=Viglet Shio
After=syslog.target

[Service]
User=viglet
Group=viglet
WorkingDirectory=/appl/viglet/shio/server
ExecStart=/appl/java/jdk21/bin/java -Xmx1g -Xms1g \
  -jar /appl/viglet/shio/server/viglet-shio.jar \
  --spring.config.additional-location=file:/appl/viglet/shio/server/viglet-shio.properties
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

:::note[Spring Boot 4]
Starting with Spring Boot 4, JAR files are no longer directly executable (`./viglet-shio.jar`). You must launch Shio using `java -jar` explicitly. The `.conf` file pattern used in older versions is no longer supported: use `--spring.config.additional-location` to load external properties instead.
:::

Enable and start the service:

```shell
systemctl daemon-reload
systemctl enable shio.service
systemctl start shio.service
```

Other useful commands:

```shell
systemctl stop shio.service
systemctl restart shio.service
systemctl status shio.service
```

### Accessing the Shio Console

Shio provides remote access through its Web application interfaces at: [http://localhost:2710](http://localhost:2710).

The administrator user is **`admin`**. Viglet Shio ships **no default password**: the `admin`
user is created without one, so there is no shipped credential to change or to forget to change.
You choose the password on first access, in one of two ways:

- **The first-access screen.** Open the console and Shio asks you to set the `admin` password
  before anything else. This is the right path for an installation someone is sitting in front of.
- **`SHIO_ADMIN_PASSWORD`.** Set it before the first start and Shio applies it for you, skipping
  the screen. This is the path for a container, a Compose file or an automated deploy: see
  [Setting the admin password without the console](#setting-the-admin-password-without-the-console).

:::note[Every surface answers 401 until a password exists]
This is deliberate, not a startup delay. Until the `admin` password is set, the REST API, the CDA
and the MCP server all refuse, so an instance can never be reachable with a credential nobody
chose.
:::

### Setting the admin password without the console

Set `SHIO_ADMIN_PASSWORD` (or the equivalent property `shio.admin.password`) and Shio applies it
to the `admin` user at startup:

```bash
docker run -p 2710:2710 -e SHIO_ADMIN_PASSWORD=change-me-please ghcr.io/openviglet/shio-ce:latest
```

```bash
java -Xmx1g -Xms1g -jar viglet-shio.jar --shio.admin.password=change-me-please
```

Three properties make it safe to leave in a Compose file or a systemd unit permanently:

- **It never overwrites a password that is already set.** A restart is a no-op, and a password an
  administrator changed in the console is not reverted by their next deployment.
- **It never prevents startup.** Unset, blank, or shorter than the six-character minimum is a log
  line and nothing more: a typo in an environment variable does not become an outage.
- **It is stored encoded**, so the value in your Compose file is not sitting in the database in
  clear text.

If the variable is ignored, the reason is in the startup log: either a password was already
configured, or the value was under six characters.

---

## Appendix A: Installation Modes

### Simple

Minimal setup using an embedded H2 database. Suitable for local development and evaluation only, not for production.

**Prerequisites:**
1. Linux server
2. Java 21
3. 50 GB HDD
4. 2 GB RAM

**Target Audience:** Development and testing environments.

### Docker Compose

Shio and its dependencies installed via Docker Compose, including:

- MariaDB: Shio system tables
- Nginx: reverse proxy on port 80
- Shio

**Prerequisites:**
1. Linux server with Docker and Docker Compose installed
2. 50 GB HDD
3. 4 GB RAM

**Target Audience:** Teams that want a complete environment without manual service installation. Suitable for QA and production.

### Kubernetes

Shio and its dependencies deployed via Kubernetes manifests (available in the `k8s/` directory), including:

- MariaDB: Shio system tables
- Nginx: reverse proxy
- Shio

**Prerequisites:**
1. Linux server with Kubernetes, or a cloud Kubernetes service (GKE, EKS, AKS)
2. 100 GB storage
3. 4 GB RAM

**Target Audience:** Cloud deployments requiring horizontal scaling and infrastructure-as-code.

### Manual Installation

Each service installed individually following this guide:

- MariaDB: Shio system tables
- Shio

**Prerequisites:**
1. One to two Linux servers
2. 50–100 GB storage per server
3. Minimum 2 GB RAM per server

---

## Appendix B: Docker Compose

You can start Shio using MariaDB and Nginx.

```shell
mvn clean package -pl shio-app
docker-compose up
```

:::tip[Deploy with the pre-built image]
The commands above build Shio from source for local development. For deployment, replace the `build:` of the `shio` service with the published image instead (`image: ghcr.io/openviglet/shio-ce:latest`) so the stack pulls the image from `ghcr.io` rather than compiling it.
:::

:::note
If you have problems with permissions on directories, run `chmod -R 777 volumes`
:::

### Docker Commands

#### Shio

```shell
docker exec -it shio /bin/bash
```

#### MariaDB

```shell
docker exec -it shio-mariadb /bin/bash
```

#### Nginx

```shell
docker exec -it shio-nginx /bin/bash
```

---
