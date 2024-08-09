# 1. Record architecture decisions

Date: 2024-04-09

## Status

Open

## Context

There are two main types of entities described by schema:

* Messages
* Models

There are a couple of different storages types:

* Key-value document oriented database
* RDBMS
* In memory data grid

All of them might have:

* 1:1
* 1:N
* N:M

Relations which must be handled

## Limitation

Kafka db sink connect isn't able to write into multiple tables.

## Decision

|     | Messages | Key-value   | RDBMS            | In memory data grid |
|-----|----------|-------------|------------------|---------------------|
| 1:1 | Embedded | Embedded    | Prefixed columns | Prefixed columns    |
| 1:N | Embedded | Extra store | Extra table      | Extra nodes         |        
| N:M | Embedded | Extra store | Extra table      | Extra nodes         |   

## Consequences

