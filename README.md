<p align="center">
  <img src="https://raw.githubusercontent.com/rwth-acis/las2peer/master/img/logo/bitmap/las2peer-logo-128x128.png" />
</p>
<h1 align="center">las2peer-Contact-Service</h1>
<p align="center">
  <a href="https://travis-ci.org/rwth-acis/las2peer-Contact-Service" alt="Travis Build Status">
        <img src="https://travis-ci.org/rwth-acis/las2peer-Contact-Service.svg?branch=master" /></a>
  <a href="https://codecov.io/gh/rwth-acis/las2peer-Contact-Service" alt="Code Coverage">
        <img src="https://codecov.io/gh/rwth-acis/las2peer-Contact-Service/branch/master/graph/badge.svg" /></a>
  <a href="https://libraries.io/github/rwth-acis/las2peer-contact-service" alt="Dependencies">
        <img src="https://img.shields.io/librariesio/github/rwth-acis/las2peer-Contact-Service" /></a>
</p>

A simple RESTful service for managing contacts and groups. The service is based on [las2peer](https://github.com/rwth-acis/LAS2peer). We provide a [polymer widget](https://github.com/rwth-acis/las2peer-frontend-user-widget) which can be used as a frontend for this service. 

Build
--------
Execute the following command on your shell:

```shell
./gradlew build
```

Start
--------

First of all make sure that you have a running instance of the [UserInformation Service](https://github.com/rwth-acis/las2peer-UserInformation-Service).

To start the Contact Service, use one of the available start scripts:

Windows:

```shell
bin/start_network.bat
```

Unix/Mac:
```shell
bin/start_network.sh
```

After successful start, Contact Service is available under

[http://localhost:8080/contactservice/](http://localhost:8080/contactservice/)

A list of available REST calls can be found in the *swagger.json* which is available at:

[http://localhost:8080/contactservice/swagger.json](http://localhost:8080/contactservice/swagger.json)


Features
--------

* Add, delete contacts
* Add, delete groups
* Add member to groups
* Edit your user information (name, userpicture)


How to run using Docker
-------------------

First build the image:
```bash
docker build -t contact-service . 
```

Then you can run the image like this:

```bash
docker run -e CONTACT_STORER_PW=*pw* -e CONTACT_STORER_NAME=*name* -p 8080:8080 -p 9011:9011 contact-service:latest
```

Replace *pw* and *name* with the username and password of the las2peer agent which should store the group information.
Note that if used to store groups once, the credentials for the agent should not change, otherwise the group data will be lost. 
The REST-API will be available via *http://localhost:8080/contactservice* and the las2peer node is available via port 9011.

In order to customize your setup you can set further environment variables.

### Node Launcher Variables

Set [las2peer node launcher options](https://github.com/rwth-acis/las2peer-Template-Project/wiki/L2pNodeLauncher-Commands#at-start-up) with these variables.
The las2peer port is fixed at *9011*.

| Variable | Default | Description |
|----------|---------|-------------|
| BOOTSTRAP | unset | Set the --bootstrap option to bootrap with existing nodes. The container will wait for any bootstrap node to be available before continuing. |
| SERVICE_PASSPHRASE | processing | Set the second argument in *startService('<service@version>', '<SERVICE_PASSPHRASE>')*. |
| SERVICE_EXTRA_ARGS | unset | Set additional launcher arguments. Example: ```--observer``` to enable monitoring. |

### Service Variables


| Variable | Default | Description |
|----------|---------|-------------|
| CONTACT_STORER_PW | *mandatory* | password of las2peer agent|
| CONTACT_STORER_NAME | *mandatory* | login name of las2peer agent|



### Web Connector Variables

Set [WebConnector properties](https://github.com/rwth-acis/las2peer-Template-Project/wiki/WebConnector-Configuration) with these variables.
*httpPort* and *httpsPort* are fixed at *8080* and *8443*.

| Variable | Default |
|----------|---------|
| START_HTTP | TRUE |
| START_HTTPS | FALSE |
| SSL_KEYSTORE | "" |
| SSL_KEY_PASSWORD | "" |
| CROSS_ORIGIN_RESOURCE_DOMAIN | * |
| CROSS_ORIGIN_RESOURCE_MAX_AGE | 60 |
| ENABLE_CROSS_ORIGIN_RESOURCE_SHARING | TRUE |
| OIDC_PROVIDERS | https://api.learning-layers.eu/o/oauth2,https://accounts.google.com |

### Other Variables

| Variable | Default | Description |
|----------|---------|-------------|
| DEBUG  | unset | Set to any value to get verbose output in the container entrypoint script. |
