[![Slack](https://img.shields.io/badge/slack-@shiocms-yellow.svg?logo=slack)](https://join.slack.com/t/shiocms/shared_invite/zt-pm6w20zq-ywfhQj0zf_mejan0_xXqJw)
[![Docker Image Version (latest semver)](https://img.shields.io/docker/v/viglet/shio?label=docker&logo=Viglet%20Shio)](https://hub.docker.com/r/viglet/shio) [![downloads](https://img.shields.io/github/downloads/ShioCMS/shio/total.svg)](https://github.com/ShioCMS/shio/releases/download/v0.3.8/viglet-shio.jar) [![Build](https://github.com/ShioCMS/shio/actions/workflows/build.yml/badge.svg)](https://github.com/ShioCMS/shio/actions/workflows/build.yml) [![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=ShioCMS_shio&metric=security_rating)](https://sonarcloud.io/dashboard?id=ShioCMS_shio) [![codecov](https://codecov.io/gh/ShioCMS/shio/branch/master/graph/badge.svg)](https://codecov.io/gh/ShioCMS/shio) [![Twitter](https://img.shields.io/twitter/follow/shiocms.svg?style=social&label=Follow)](https://twitter.com/intent/follow?screen_name=shiocms)

![shio_banner.png](https://shiocms.github.io/shio/img/shio_banner.png) 
------

**Viglet Shio CMS** - Model Content, use GraphQL and Create Site using Javascript with Native Cache and Search.

Shio (pronounced [strong/ʃiː/ weak/ʃɪ/ o])

**If you'd like to contribute to Viglet Shio, be sure to review the [contribution
guidelines](CONTRIBUTING.md).**

**We use [GitHub issues](https://github.com/ShioCMS/shio/issues) for tracking requests and bugs.**

# Installation

## Pre-reqs
1. Install Java 17
2. Install Git Client
3. Install NPM
4. Install Angular CLI
```shell
$ npm install -g @angular/cli
```

## Download

```shell
$ git clone https://github.com/ShioCMS/shio.git
$ cd shio
```

## Deploy 

### 1. Runtime

Use Gradle to execute Shio CMS, without generate jar file.

```shell
$ ./mvnw spring-boot:run
```


### 2. Or Generate JAR File

Use Gradle to generate Shio CMS executable JAR file.

```shell
$ ./mvnw package
```

#### 2.1 Run

To run Shio CMS executable JAR file, just execute the following line:

```shell
$ java -jar build/libs/viglet-shio.jar
```

## Viglet Shio CMS
* Administration Console: [http://localhost:2710](http://localhost:2710).

> login/password: admin/admin

* Sample Site: [http://localhost:2710/sites/viglet/default/en-us](http://localhost:2710/sites/viglet/default/en-us).
