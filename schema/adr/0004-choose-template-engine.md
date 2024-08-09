# 4. Choose template engine

Date: 2024-04-09

## Status

Accepted

0003 [3. Choose generator language](0003-choose-generator-language.md)

## Context

Find a suitable template engine

## Options

* Thymeleaf - tag based
* FreeMarker - tag oriented
* Velocity - awkward documentation
* Mustache - logic less
* Handlebars - logic less
* Pug - tag based
* Pebble

## Decision

Pebble https://pebbletemplates.io/

## Consequences

### Pros

* Twig based similar to helm templates
* Macros
* Inheritance
* Includes
* Piping
* Concise syntax
* Performant

### Cons

* Couple of open issues
* Isn't the most popular
