RPKI - Core
===========

License
-------

Copyright (c) 2008-2023 RIPE NCC
All rights reserved.

This software, including all its separate source codes, is licensed under the
terms of the BSD 3-Clause License. If a copy of the license was not distributed
to you, you can obtain one at
https://github.com/RIPE-NCC/rpki-core/blob/main/LICENSE.txt.

# Overview

This project is the core certification authority (CA) for the RIPE NCC RPKI infrastructure. It maintains and runs all
hosted member CAs as well as the RIPE NCC "production CA" (containing only the resources assigned to RIPE) and the
"All Resources CA" (the parent of the production CA with all Numbered Resources).

Non-hosted CAs also access this system to retrieve or revoke their resource certificates through the up-down protocol.

**Internal note**: The deployment is further documented in a [wiki page](https://marvin.ripe.net/display/SWE/Certification+Software+as+a+Service).

## About this repository

This repository contains the source code for the RIPE NCC certification. We
strive to publish as many components as possible with reasonable effort. Some
elements or information are not included, either because of our threat model or
because we can not publish them.

This repository and its git history **MUST NOT** contain:
  * URLs or hostnames for internal services that have not been disclosed through
    public sources (e.g. zone transfers) before.
  * Secrets (API Keys, passwords, ...) except for those required for integration tests.
  * Internal information (names, uids, ...) that are not public.
  * Details on the deployment (hostnames, IP addresses) and deployment environment.
    * It is acceptable to publish what _types_ of environments we have (production, staging, test, ...).
  * Proprietary libraries that we can not publish, mainly related to HSM usage.

The main goal of this repository is to be open about what code runs the RIPE NCC
CA. We try to minimise the dependency of this project on internal services but
will develop it to fit our internal roadmap. If you want to run this software in
production, contact us.

### Source code

The repository used for daily operations is hosted internally at RIPE NCC. The source code that runs in
production is published to the [public repository](https://github.com/RIPE-NCC/rpki-core).

To ensure that none of those items listed above are accessible in the history of the repository, the public
repository uses a new baseline. The first commit contains all source code running in production at starting
point. All change sets are tracked on top of this baseline, so that the `main` branch should reflect the code
that RIPE NCC runs in production.

Source code is published during the automated deployments, by running `scripts/publish-source-code`.

## Security

Reports of previous security assessments of this project, and related projects
are published at [RPKI security and compliance](https://www.ripe.net/manage-ips-and-asns/resource-management/rpki/security-and-compliance).

If you have found a security issue, you can report these under the RIPE NCC
[responsible disclosure policy](https://www.ripe.net/support/contact/responsible-disclosure-policy).
If you have other questions you can contact the RPKI team at the RIPE NCC using
the [contact form](https://www.ripe.net/support/contact). Team members also
participate in various other channels, such as SIDROPS or the RPKI discord.

## Architecture

The system is written as a regular Spring Boot application using JPA as the database access layer and PostgreSQL as the
actual database implementation.

The system is accessed mostly through a REST API to manage hosted certificate authorities and ROA configuration.
Non-hosted CAs are accessed using the up-down protocol. Finally, there are a few administrative endpoints.

## Consistency, Transactions, Locking

A certification authority needs to be _highly_ consistent. Do achieve this each certification authority is a _consistency
boundary_ represented as an aggregate root (see Domain-Driven Design). To ensure high consistency we use _pessimistic
locking_. A certification authority is _locked_ before we do any processing. This is mainly handled by
the `LockCertificateAuthorityHandler` which is invoked before a command is processed. However, some processes by-pass
the command infrastructure and perform the required locking operations manually.

Normally only a single CA needs to be locked during a transaction. However, sometimes a CA needs to invoke its _parent_
CA, for example to issue a new certificate or revoke an old one. In this case the
_child_ must always be locked before the _parent_, to avoid deadlocks.

One exception is the `PublicRepositoryPublicationServiceBean` which locks multiple CAs when needed and does not yet
strictly lock a child CA before a parent CA.

As a backup measure we use JPA _optimistic locking_ using the `@Version version` column. This prevents many lost updates
due to the "read-modify-update" cycle inherent to the JPA persistence mechanism. However, if we ever get an optimistic
locking exception the cause is most likely a lack of proper pessimistic locking of the CA.

We also implement various database constraints to ensure consistency of the database. These constraints have grown over
the years in an ad-hoc manner. Often full constraints cannot be implemented this way due to the use of JPA which doesn't
make it easy to enforce various database update ordering to ensure constraints are not violated temporarily.

## Publication, manifests, and CRLs

One of the primary purposes of the system is to publish a fully valid, consistent set of RPKI objects (see RFC 6486bis,
https://datatracker.ietf.org/doc/html/draft-ietf-sidrops-6486bis-07). Internally certificates and ROA objects are
generated based on the configuration as the system is running. When configuration, resources, or time changes,
certificates and ROAs are generated and revoked.

Manifests and CRLs are created "just-in-time", just before publishing the CA's RPKI objects. This is done as part of the
`CertificateRepositoryManagementServiceBean` and `CertificateRepositoryPublicationServiceBean`. The reason for this is
that the manifest and CRL both need to be fully consistent with each other and with all the published objects that are on
the manifest. The best time to determine that is when we know exactly what will be published.


## How to set up RPKI development environment

### Docker

You can run RPKI core and all required components with docker.

```
> gradle build
> docker-compose up
```

This builds the necessary docker images and starts the container configuration as specified in
the `docker-compose.yml`. When you make modifications to the system, don't forget to rebuild the
docker image with `docker-compose build` (or `docker-compose up --build`).

### Hardware Security Module

For secure key signing the CA software integrates with a Hardware Security
Module (HSM). This is used by RIPE on production systems.

To run the CA software locally or on test systems, the `SUN` keystore provider
is used. The standard build output and default configuration support this
keystore.

To support the Thales HSM the CA software must include some proprietary
integration code. This is build into a feature extension in the `hsm` folder, and the
CA software must be build with this feature included.

```
> HSM_IMPL=thales gradle build
```

Note that this fails when the corresponding proprietary library is not available
in one of the configured repositories.

### Data directories on your laptop

By default, locally the repository data is stored in `$HOME/export/bad/certification`. The folder is
automatically created on the first run.

You can change the location with environment variable `ONLINE_REPOSITORY_DIRECTORY`, and other
standard spring-boot means of configuration overriding.

### Setup the database

You need a configured, running PostgreSQL v11.x database with a certdb user:

    createuser -R -S -D certdb
    createdb -O certdb -E utf8 certdb

Resetting the database is easy. Just drop and recreate using the Postgres commands:

    dropdb certdb
    createdb -O certdb -E utf8 certdb

When running integration tests, you'll need also certdb_test database:

    createdb -O certdb -E utf8 certdb_test

Notes:

- Use the system shell, not Postgres'. The syntax is different from one to another and the user might not be created as supposed.

### Integrations with RIPE services

Some configuration properties default to integrating with internal RIPE services. When running outside of a RIPE
environment you cannot access these services.

__Resource cache__

Configure the resource cache to use a static source file.

```
> export RESOURCE_SERVICES_SOURCE=static
```

This defaults to use a builtin resource cache. Optionally, provide a custom file.

```
> export RESOURCE_SERVICES_STATIC_FILE=file:///path/to/file
```

__CA names__

To humanize CA names RPKI core tries to lookup the name in RIPEs member database. When this service is unavailable,
the membership ID is used for CA name.

### OAuth2 authentication

To enable OAuth2 authentication in your local setup you need add a new
application in an Okta developer account and/or Github and add the
following to `application-local.yml`:

```yaml
spring.security.oauth2.client:
    provider.okta.issuer-uri: '<okta-provider-uri, e.g. https://dev-<DEVID>.okta.com/oauth2/default'
    registration:
        okta:
            client-id: '<okta-client-id>'
            client-secret: '<okta-client-secret>'
            scope:
                - openid
                - profile
                - email
        github:
            client-id: '<github-client-id>'
            client-secret: '<github-client-secret>'
```

Also change the `admin.authorization.enabled` to `true`.

Make sure you do not check in your secrets!


### Start the certification application

* Via command line:

    `gradle bootRun`

* Via IDE:

    Execute the main class: `RpkiBootApplication`

Notes:

- You should point `RIPE_PUBLICATION_SERVER_SRC_PATH` to your local copy of [RPKI Publication Server], if not in `${HOME}/src/ripe/rpki/rpki-publication-server`.
- In the development environment the background services are disabled by default. They can be enabled explicitly by activating the `activeBackgroundServices` spring profile.
- If you want to check for up to date dependencies: `gradle dependencyUpdate`

In development you might want to enable continuous compilation with
gradle using `gradle build --continuous -xtest`. The `gradle bootRun`
task with automatically restart the application after recompilation is
complete. Note that both commands must run concurrently.


### Set up the all resources and production CAs

1. Go to [http://localhost:8080/certification/](http://localhost:8080/certification/)
2. After authenticating with "admin" as password there will be a
   notice is "No All Resources CA found for this installation". Click "Create".
3. Navigate to the 'System Status'. There will be a notice about no production CA
   present. Click 'Create...'. Do NOT create the sign request yet.

### Populate the resource cache

1. Navigate to 'System Status' page
2. Start the 'Resource Cache Update Service'. It will take a few
   minutes to complete.
3. Navigate to 'Upstream CA Management' and click
  'Generate sign request for NEW resources'.


### Process the sign request

1. Download, build and extract the [RPKI trust anchor tool](https://github.com/RIPE-NCC/rpki-ta-0).
2. Initialize the trust anchor:

    `APPLICATION_ENVIRONMENT=local ./ta.sh --env local --initialise`

3. Navigate to the 'Upstream CA Management' page and download the sign request (say we save download as `request.xml`).
4. Process it with the ta tool:

    `APPLICATION_ENVIRONMENT=local ./ta.sh --force-new-ta-certificate --request request.xml --response response.xml`

5. Upload the response using curl (the upload function on 'Upstream CA Management' does not seem to work):

```
# The testing API key below is included intentionally and not a secret.
curl -i -X POST -F "file=@response.xml" \
    -H 'ncc-internal-api-key: BAD-TEST-D2Shtf2n5Bwh02P7' \
    "http://localhost:8080/certification/api/upstream/upload/"
```

### Using the Validator on your development repository

1. Start the rsync daemon:

    `${RPKI_RIPE_NCC_DIR}/scripts/rsync start`

2. Navigate to the 'System Status' page and run the 'Repository Update Service' (Publication Service).
3. Download the latest release of the RPKI validator [here](http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=net.ripe.rpki&a=rpki-validator-app&v=LATEST&c=dist&p=tar.gz).
4. Un-tar it:

    `tar xzf rpki-validator-app-${version}-dist.tar.gz`

5. Add the trust anchor TAL file to the `conf/tal/` directory.
6. Optional: remove the other .tal files from the `conf/tal/` directory.
7. In the configuration file (`conf/rpki-validator.conf`), set `ui.http.port` to a free port.
8. Start the validator:

    `${VALIDATOR_DIR}/rpki-validator.sh start`

### Set up a Member CA

1. Setup the `ripe-portal` project. Change the `RpkiClient.scala` to
   use `"ncc-internal-api-key"` as header for the API key. Currently
   the `API_KEY_HEADER` constant it uses still refers to the
   `"X-API-KEY"` header. Also change the `rpki.api.uriTemplate` in
   `application.local.conf` (or whatever file you are using) to
   `http://localhost.ripe.net:8080/certificatin/api`.

2. Start the `ripe-portal` and go to `Resources > RPKI dashboard`. Now
   you should agree to the terms and conditions and created a hosted
   CA, create ROAs, etc.


### Resetting your local development system

- Stop your instance.
- Clean stuff on disk:

    `rm -rf $HOME/export/bad/certification`

- Drop and re-create the database:

    `dropdb certdb`

    `createdb -O certdb certdb`

- Start your instance and repeat set up as described above.


### Save a working snapshot

At any point in time you might want to save a working snapshot in order to come back to that state.

Backup the repository:

    cp -r $HOME/export/bad/certification/ $BACKUP_DIR/certification.bak

Backup the database:

    pg_dump -Fc certdb > $BACKUP_DIR/certdb.init.bak

Restore the database:

    pg_restore -d certdb $BACKUP_DIR/certdb.init.bak


## Useful links

[RPKI Tools & resources](http://www.ripe.net/lir-services/resource-management/certification/tools-and-resources/)
[RPKI Monitoring](http://ba-apps.ripe.net/certification/monitoring)
[RPKI Admin UI @ core-1](http://core-1.rpki.ripe.net:8080/certification)
[RPKI Admin UI @ core-2](http://core-2.rpki.ripe.net:8080/certification)
[RPKI Operational Documentation](https://gitlab.ripe.net/rpki/rpki-doc)
[RPKI Publication Server](https://github.com/RIPE-NCC/rpki-publication-server)

- The application has a prometheus endpoint under the relative path `/actuator/prometheus`, i.e. http://localhost:8080/certification/actuator/prometheus for localhost.
