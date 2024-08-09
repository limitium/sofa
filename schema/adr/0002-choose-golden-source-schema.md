# 2. Choose golden source schema

Date: 2024-04-09

## Status

Accepted

## Context

Single place to define messages and models is required

## Decision

Use default Avro json schema

## Consequences

### Pros

* JSON easy to write
* Reach enough to describe low level entities
* Schema parser already exists
* Easy to extend with a custom metadata

### Cons

* JSON is a non-human friendly format
* Schema dependencies(includes) must be handled manually