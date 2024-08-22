# 5. Choose application framework

Date: 2024-04-09

## Status

Accepted

## Context

Cli application should handle resources, schema dependencies, pluggable generators with custom interceptors. Manage OS processes.

## Options

* None + minimal set of libs
* Spring boot cli

## Decision

Implement in java without frameworks

## Consequences

### Pros

* Startup and generation time
* Application size

### Cons

* Custom resource management
* Custom plugin extensibility
* Custom(guice) IoC
