# folio-module-sidecar

Copyright (C) 2023-2024 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Table of contents

* [Introduction](#introduction)
* [Header Validation](#header-validation)
* [Compiling](#compiling)
    * [Creating a native executable](#creating-a-native-executable)
* [Running It](#running-it)
* [Environment Variables](#environment-variables)

## Introduction

`folio-module-sidecar` provides following functionality:

* This project uses [Quarkus](https://quarkus.io/)
* module independent, uses Okapi Module Descriptors for self-configuration
* Ingress request routing for underlying module (specified using environment variables)
* Egress request routing for module-to-module communication

## Header Validation

The sidecar performs validation of incoming HTTP request headers to ensure data integrity and security. As part of this validation:

* Duplicate x-okapi-* headers are rejected
* If a request contains multiple headers with the same x-okapi-* name, the request will be denied with a 400 Bad Request response
* This prevents potential security issues and ensures consistent header processing

## Compiling

The application can be compiled using:

[rest of the original content remains exactly the same...]