![las2peer](https://rwth-acis.github.io/las2peer/logo/vector/las2peer-logo.svg)

# las2peer-Contact-Service 

[![Build Status](https://travis-ci.org/rwth-acis/las2peer-Contact-Service.svg?branch=master)](https://travis-ci.org/rwth-acis/las2peer-Contact-Service) [![Code Coverage](https://codecov.io/gh/rwth-acis/las2peer-Contact-Service/branch/master/graph/badge.svg)](https://codecov.io/gh/rwth-acis/las2peer-Contact-Service) [![Dependencies](https://img.shields.io/librariesio/github/rwth-acis/las2peer-Contact-Service)](https://libraries.igo/github/rwth-acis/las2peer-contact-service)

A simple RESTful service for managing contacts and groups. The service is based on [las2peer](https://github.com/rwth-acis/LAS2peer). We provide a [polymer widget](https://github.com/rwth-acis/las2peer-frontend-user-widget) which can be used as a frontend for this service. 

Build
--------
Execute the following command on your shell:

```shell
ant all 
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
