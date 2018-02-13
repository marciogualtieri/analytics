# Analytics Service

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**

- [Overview](#overview)
- [Design](#design)
  - [Data Repository](#data-repository)
  - [Caching Service](#caching-service)
- [Running Tests](#running-tests)
- [Coverage Reports](#coverage-reports)
- [Continuous Integration](#continuous-integration)
- [Running the App](#running-the-app)
- [Deployment](#deployment)
  - [Building the App](#building-the-app)
  - [Testing the Build](#testing-the-build)
  - [Changing the Database Configuration](#changing-the-database-configuration)
  - [Amazon AWS](#amazon-aws)
    - [Installing Terraform](#installing-terraform)
    - [Deploying to AWS](#deploying-to-aws)
    - [Un-deploying from AWS](#un-deploying-from-aws)
    - [Testing the Deployment](#testing-the-deployment)
- [Developer's Guide](#developers-guide)
  - [Creating an Empty App](#creating-an-empty-app)
  - [Adding Play-Slick Support](#adding-play-slick-support)
  - [Validating Form Data](#validating-form-data)
  - [Coverage Reports Configuration](#coverage-reports-configuration)
  - [GitHub Camo](#github-camo)
  - [Adding a TOC to this Document](#adding-a-toc-to-this-document)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Overview

The purpose of this project is to showcase my back-end development skills. Specifically on the technology side:

* [Functional Programming](https://en.wikipedia.org/wiki/Functional_programming) in [Scala](https://www.scala-lang.org/).
* Design and development of [REST services](https://en.wikipedia.org/wiki/Representational_state_transfer) with the [Play Framework](https://www.playframework.com/).
* Use of [Play-Slick](https://www.playframework.com/documentation/2.6.x/PlaySlick) for [object-relational mapping](https://en.wikipedia.org/wiki/Object-relational_mapping).
* Use of [AKKA](https://akka.io/) to deal with concurrency (particularly a concurrent data store caching service).

On the processes side, this project also showcases my ability of developing a [MVP](https://www.agilealliance.org/glossary/mvp/) following [Agile](https://en.wikipedia.org/wiki/Agile_software_development) principles:

* Following the [open/close principle](https://en.wikipedia.org/wiki/Open/closed_principle).
* Following the [test-driven development process](https://en.wikipedia.org/wiki/Test-driven_development).
* Reporting test coverage with [Jacoco](http://www.eclemma.org/jacoco/).
* Following [continuous integration](https://en.wikipedia.org/wiki/Continuous_integration) using [CircleCI](https://circleci.com/).
* Creating a deploy-able build (to [Amazon AWS](https://aws.amazon.com/) using [terraform](https://www.terraform.io/)) for quick and constant feedback from the end-user.


The REST service is comprised of a single `/analytics` end-point which supports following two methods:

**POST**

Persists analytics data into the data repository and supports the following form parameters:

* timestamp: in milliseconds since epoch.
* user: a string identifier
* event: a string, either `click` or `impression`.

According with the requirements, we should "track the event in our data store", which, in my understanding, translates to: the event should be fully saved to the data store. If we simply update the statistics for each event, we wouldn't be truly tracking the events, given that the timestamp of each individual event would be lost.

The specification appear to suggest that the parameters in the POST call were query parameters, but given that the usual is passing url-encoded form parameters in the body for a POST request, that's what I have opted for. This choice is open to discussion during the reviewing process.

**GET**

Retrieves event statistics for the hour of the provided timestamp. Supports a single query parameter:

* timestamp: in milliseconds since epoch.

This request should return the following statistics as plain text:

    unique_users,<unique usernames count>
    clicks,<click count>
    impressions,<impression count>

Also according with the requirements, over 90% of the requests have timestamps that correspond to the moment in time the requests were made. We can take advantage of this traffic pattern to avoid unnecessary calls to the data store: By caching the event statistics in memory, we don't need to call the data store to aggregate the events and generate the statistics for every GET call.

## Design

For my own future reference, syntax for Plant UML diagrams can be found [here](http://plantuml.com/class-diagram).

### Data Repository

The Play Framework follows the [MVC pattern](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller), so this framework has a well defined way to create views, models and controllers. Most of the design goes into the model, given that the view and controller are very standard. Let's take a look at the model:

![Data Repository Class Diagram](http://www.plantuml.com/plantuml/proxy?src=https://raw.githubusercontent.com/marciogualtieri/analytics/master/uml/data_repository.plantuml)

First of all, note the `Future` objects: All the data store operations are non-blocking/asynchronous operations.

Also note I have defined an interface (actually a `trait` in Scala), so the application complies with open/close principles: By creating an abstraction for the `EventRepository`, we don't have to commit to a particular implementation. The application can be easily changed to support a different data repository (or ORM) without changing neither the caching service nor the controllers that depend on it (only `AnalyticsController` at the moment).

`SlickEventRepository` (a data repository implementation that uses Play-Slick) is an actual implementation.

For more details on this architecture choice, refer to [this article](http://blog.cleancoder.com/uncle-bob/2016/01/04/ALittleArchitecture.html) from [Robert C. Martin](https://en.wikipedia.org/wiki/Robert_C._Martin).

Note also that `CachedEventRepository` also implements the same interface, just like any other concrete data repository would.

Because of this architecture, either `CachedEventRepository` or `SlickEventRepository` can be used by the controller as the data repository.

As a matter of fact, I developed `SlickEventRepository` first, wrote all functional tests for the end-point, and had a fully deploy-able application before I even started designing the caching service.

### Caching Service

As discussed earlier, `CachedEventRepository` was added last to the project as an enhancement. Note that the functional tests for the end-point are agnostic regarding the data repository, they should behave the same, independently of the data repository used.

The fact that one can't really check if the caching service is working by inspecting the end-point (only a difference in performance for cached and non-cached requests would be noticeable) is the reason I'm using mocks to test it, specifically [mokito](http://site.mockito.org/).

As a rule of thumb, I try to avoid the use of mocks in tests so they don't become coupled to production code and brittle. For more details on this, refer to [this talk](https://vimeo.com/68375232) from Ian Cooper.

In this particular case, the use of mocks is fully justified though: By using mocks we can be sure that the caching service is using cached data or fetching data from the repository depending on the current timestamp and timestamp of the request.

Note also that I have defined an object for retrieving the current timestamp: `Clock`. This decoupling allows us to mock the current timestamp and thus simulate some of the scenarios for the caching service (particularly when an particular hour in the day is over and the current cached statistics expires).

Given that the cached data is shared across different threads for different requests, you might have notice that we need to deal with concurrency.

That's the reason why I'm using AKKA, having defined the following actor and messages:

![Caching Service Class Diagram](http://www.plantuml.com/plantuml/proxy?src=https://raw.githubusercontent.com/marciogualtieri/analytics/master/uml/caching_actor.plantuml)

The message's names fully describe their purpose. These messages are sent to the actor to modify the cache and read the cached data as required.

## Running Tests

To run tests, execute the following command:

    sbt test

You should get an output similar to the following:

    [info] Loading settings from plugins.sbt,scaffold.sbt ...
    [info] Loading project definition from /home/gualtief/workspace/Narrative/analytics/project
    [info] Loading settings from build.sbt ...
    [info] Set current project to analytics (in build file:/home/gualtief/workspace/Narrative/analytics/)
    [info] Instrumenting 41 classes to /home/gualtief/workspace/Narrative/analytics/target/scala-2.12/jacoco/instrumented-classes
    [info] IntegrationSpec:
    [info] Application
    [info] - should work from within a browser
    [info] AnalyticsControllerSpec:
    [info] AnalyticsController POST
    [info] - should persist an event into the repository
    [info] - should fail request with form parameter missing value
    [info] - should fail request with form parameter with empty value
    [info] - should fail request with invalid event form parameter
    [info] AnalyticsController GET
    [info] - should retrieve event counts from repository for the hour
    [info] CachedEventRepositorySpec:
    [info] Caching
    [info] - should load cached data on creation
    [info] - should return cached data for current hour
    [info] - should return repository data for past hour
    [info] - should update cached data with new events
    [info] - should reload cached data when clock advances to next hour
    [info] ScalaTest
    [info] Run completed in 16 seconds, 49 micurl -v http://ec2-34-244-147-122.eu-west-1.compute.amazonaws.com:9000/analytics?timestamp=1445455680000lliseconds.
    [info] Total number of tests run: 11
    [info] Suites: completed 3, aborted 0
    [info] Tests: succeeded 11, failed 0, canceled 0, ignored 0, pending 0
    [info] All tests passed.
    [info] Passed: Total 11, Failed 0, Errors 0, Passed 11
    [success] Total time: 20 s, completed 12-Feb-2018 08:58:12

## Coverage Reports

I'm using the [sbt-jacoco](https://www.scala-sbt.org/sbt-jacoco/) plugin to generate coverage reports.

To generate the reports, execute the following command:

    sbt jacoco

You should get an output similar to the following:

    [info] ------- Jacoco Coverage Report -------
    [info]
    [info] Lines: 74.36% (>= required 0.0%) covered, 70 of 273 missed, OK
    [info] Instructions: 70.43% (>= required 0.0%) covered, 832 of 2814 missed, OK
    [info] Branches: 37.72% (>= required 0.0%) covered, 71 of 114 missed, OK
    [info] Methods: 65.12% (>= required 0.0%) covered, 45 of 129 missed, OK
    [info] Complexity: 51.08% (>= required 0.0%) covered, 91 of 186 missed, OK
    [info] Class: 71.43% (>= required 0.0%) covered, 10 of 35 missed, OK
    [info]
    [info] Check /home/gualtief/workspace/analytics/target/scala-2.12/jacoco/report for detailed report

HTML reports will be available at the folder `target/scala-2.12/jacoco/report/html/` after running Jacoco.

The coverage isn't 100% because of two reasons: case classes (doesn't make sense to write tests for them) and generated code (from the Play Framework).

If you look at the HTML reports more carefully, you will notice that the relevant code is being fully covered.

## Continuous Integration

This project uses [CircleCI](https://circleci.com) for this purpose. You may see all builds [here](https://circleci.com/gh/marciogualtieri/analytics).

## Running the App

To run the app in development mode, execute the following command:

    sbt run

You should get an output similar to the following:

    [info] Loading settings from plugins.sbt,scaffold.sbt ...
    [info] Loading project definition from /home/gualtief/workspace/Narrative/analytics/project
    [info] Loading settings from build.sbt ...
    [info] Set current project to analytics (in build file:/home/gualtief/workspace/Narrative/analytics/)

    --- (Running the application, auto-reloading is enabled) ---

    [info] p.c.s.AkkaHttpServer - Listening for HTTP on /0:0:0:0:0:0:0:0:9000

    (Server started, use Enter to stop and go back to the console...)

Which means that the service is up and running. You may now send requests to the end point using [cURL](https://curl.haxx.se/) or your preferred tool:

**Persisting an event:**

    curl -v -H "Content-Type: application/x-www-form-urlencoded" \
    -d 'user=rick.sanchez&event=click&timestamp=1445455680000' \
    http://localhost:9000/analytics

You should get an output similar to the following:

    *   Trying 127.0.0.1...
    * Connected to localhost (127.0.0.1) port 9000 (#0)
    > POST /analytics HTTP/1.1
    > Host: localhost:9000
    > User-Agent: curl/7.47.0
    > Accept: */*
    > Content-Type: application/x-www-form-urlencoded
    > Content-Length: 58
    >
    * upload completely sent off: 58 out of 58 bytes
    < HTTP/1.1 204 No Content
    < Referrer-Policy: origin-when-cross-origin, strict-origin-when-cross-origin
    < X-Frame-Options: DENY
    < X-XSS-Protection: 1; mode=block
    < X-Content-Type-Options: nosniff
    < Content-Security-Policy: default-src 'self'
    < X-Permitted-Cross-Domain-Policies: master-only
    < Date: Sat, 10 Feb 2018 17:56:32 GMT
    <
    * Connection #0 to host localhost left intact

Note the HTTP response with status 204 (NO CONTENT).

**Retrieving statistics:**

    curl -v http://localhost:9000/analytics?timestamp=1445455680000

You should get an output similar to the following:

    *   Trying 127.0.0.1...
    * Connected to localhost (127.0.0.1) port 9000 (#0)
    > GET /analytics?timestamp=1445455680000 HTTP/1.1
    > Host: localhost:9000
    > User-Agent: curl/7.47.0
    > Accept: */*
    >
    < HTTP/1.1 200 OK
    < Referrer-Policy: origin-when-cross-origin, strict-origin-when-cross-origin
    < X-Frame-Options: DENY
    < X-XSS-Protection: 1; mode=block
    < X-Content-Type-Options: nosniff
    < Content-Security-Policy: default-src 'self'
    < X-Permitted-Cross-Domain-Policies: master-only
    < Date: Sat, 10 Feb 2018 17:58:53 GMT
    < Content-Type: text/plain; charset=UTF-8
    < Content-Length: 43
    <

    unique_users,1
    clicks,0
    impressions,2
    * Connection #0 to host localhost left intact

Note the HTTP response with status 200 (OK) and the event statistics.

## Deployment

### Building the App

Execute the following command builds a binary version of your application that you can deploy to a server:

    sbt universal:packageZipTarball

You should get a `*.tar.gz` file named `./target/universal/analytics-${VERSION}.tar.gz`.

### Testing the Build

To unpack it:

    tar -xvzf analytics-${VERSION}.tar.gz

You will need an application secret, which you may generate by running the following command:

    export APPLICATION_SECRET=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)

To start the server, execute the startup scrip `./bin/analytics` as following:

    ./analytics-${VERSION}/bin/analytics -Dplay.http.secret.key=${APPLICATION_SECRET} -Dplay.evolutions.autoApply=true

These are only general instructions about how to start the service in any server. We will be deploying to AWS using terraform in the section that follows.

Another way to check if the application can be started in production mode is:

    sbt runProd

### Changing the Database Configuration

At the moment the application uses [H2](http://www.h2database.com/html/main.html) as the database:

    slick.dbs.default.profile="slick.jdbc.H2Profile$"
    slick.dbs.default.db.driver="org.h2.Driver"
    slick.dbs.default.db.url="jdbc:h2:mem:play"

You may change it by adding the proper configuration to `application.conf`. As an example, here's the configuration for [PostgreSQL](https://www.postgresql.org/):

    slick.dbs.default.profile="slick.jdbc.PostgresProfile$"
    slick.dbs.default.db.driver="org.postgresql.Driver"
    slick.dbs.default.db.url=${JDBC_DATABASE_URL}

You would also need to add the database's library dependency to `build.sbt`:

    libraryDependencies += "postgresql" % "postgresql" % "9.1-901-1.jdbc4"

### Amazon AWS

We will be using [Terraform](https://www.terraform.io) to deploy our application to [Amazon AWS](https://aws.amazon.com/).

#### Installing Terraform

To install it follow [these instructions](https://www.terraform.io/intro/getting-started/install.html).

Once installed, execute the following command to install its plugins:

    terraform init

#### Deploying to AWS

Export the application's version (change it accordingly depending on the current version):

    export APPLICATION_VERSION=1.0-SNAPSHOT

The following command will generate a random application secret, but you may define your own:

    export APPLICATION_SECRET=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)

You also will need an Amazon AWS key pair. The terraform configuration uses a key pair named `analysis`.

You may create your own key pair named `analysis` or change `./terraform/main.tf` to reflect your own key pair.

Once you have the key pair, you will need import it to your local machine using the AWS console. In the examples bellow, the imported key pair is named `/home/gualtief/tmp/analytics.pem`.

From the terraform folder, execute the following command:

     terraform apply -var "APPLICATION_SECRET=${APPLICATION_SECRET}" -var "PRIVATE_KEY_FILE=/home/gualtief/tmp/analytics.pem"

#### Un-deploying from AWS

To delete the deployment, execute the following command:

    terraform destroy -var "APPLICATION_SECRET=${APPLICATION_SECRET}" -var "PRIVATE_KEY_FILE=/home/gualtief/tmp/analytics.pem"

#### Testing the Deployment

I have already deployed the application to AWS and it can be found [here](http://ec2-34-244-147-122.eu-west-1.compute.amazonaws.com:9000/).

If you are deploying a new instance, you have to change the commands to reflect your own server's URL.

**Persisting an Event:**

    curl -v -H "Content-Type: application/x-www-form-urlencoded" \
    -d 'user=rick.sanchez&event=click&timestamp=1445455680000' \
     http://ec2-34-244-147-122.eu-west-1.compute.amazonaws.com:9000/analytics

**Retrieving Statistics:**

    curl -v http://ec2-34-244-147-122.eu-west-1.compute.amazonaws.com:9000/analytics?timestamp=1445455680000

## Developer's Guide

For my own reference, I'm recording the steps necessary to create the app from scratch.

### Creating an Empty App

We will be using the [Play Framework 2.6](https://www.playframework.com/documentation/2.6.x).

Execute the following command from the terminal:

    sbt new playframework/play-scala-seed.g8

Follow the instructions on the screen. As a rule of thumb, you only need to change the project's name. The default settings will do just fine.

### Adding Play-Slick Support

We will be using [Play-Slick 2.6](https://www.playframework.com/documentation/2.6.x/PlaySlick). Add the following lines to your `build.sbt` file:

    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-slick" % "3.0.3",
      "com.typesafe.play" %% "play-slick-evolutions" % "3.0.3",
      "com.h2database" % "h2" % "1.4.196"
    )

### Validating Form Data

Refer to the [documentation](https://www.playframework.com/documentation/2.6.x/ScalaForms) for details.

The Play Framework includes many built-in validators for form parameters and also allows building custom ones.

### Coverage Reports Configuration

Add the following line to your plugins.sbt:

    addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.0.3")

As per [this section in the documentation](https://www.scala-sbt.org/sbt-jacoco/getting-started.html#setting-minimum-coverage-levels), we could make jacoco fail if the coverage doesn't comply with some minimum standards. That would advisable when the project reaches a more mature state.

### GitHub Camo

If you have any generate images in your markdown files on GitHub (such as from [Plant UML server](http://plantuml.com/server), for instance), you might have problems updating diagrams.

GitHub aggressively caches images using a service of its own named [Camo](https://help.github.com/articles/about-anonymized-image-urls/).

The solution for this issue is deleting the image from Camo's cache:

    curl -X PURGE <cached image URL>

You can get the URL by right-clicking the image on your GitHub's markdown page.

### Adding a TOC to this Document

For my own future reference, here's a nice way to add a TOC to a markdown file.

Install [DocToc](https://github.com/thlorenz/doctoc):

    npm install -g doctoc

Run DocToc on the markdown file:

    doctoc README.md

Re-run the command to update the TOC with changes in the markdown file.