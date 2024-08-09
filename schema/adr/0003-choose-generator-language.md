# 3. Choose generator language

Date: 2024-04-09

## Status

Accepted

0003 [4. Choose template engine](0004-choose-template-engine.md)

## Context

Needs to find a way to define different generators to produce new artifacts from the golden source schema

## Options

* Java
* Javascript
* Groovy
* Python
* Ruby
* Template engines

## Decision

Use template engine to define new artifacts

## Consequences

### Pros

* Cleaner code
* Closer look to the end artifact

### Cons

* Less flexible than any language
* Extensibility is predefined in template engine
