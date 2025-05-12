# REST Java API

The REST Java API provides Java-based REST APIs for the mirror node. Originally, the mirror node REST API was written
in JavaScript and contained within the `rest` module. This new module is intended to contain new APIs
and any existing JavaScript APIs once converted to Java. Eventually, all JavaScript APIs will be converted to
Java and the two modules can be merged.

## Technologies

This module uses [Spring Boot](https://spring.io/projects/spring-boot) for its application framework. To serve the
APIs, [Spring Web](https://docs.spring.io/spring-boot/docs/current/reference/html/web.html)
is used with annotation-based controllers. [Spring Data JPA](https://spring.io/projects/spring-data-jpa) with Hibernate
is used for the persistence layer.
