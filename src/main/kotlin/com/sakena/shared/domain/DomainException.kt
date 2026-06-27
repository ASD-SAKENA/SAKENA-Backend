package com.sakena.shared.domain

/**
 * Base type for all domain-level errors. Living in the shared kernel keeps the
 * domain layer free of any web/framework dependency while still letting the
 * presentation layer translate failures into HTTP responses in one place.
 */
open class DomainException(message: String) : RuntimeException(message)

/** The requested aggregate/entity does not exist. Maps to HTTP 404. */
open class EntityNotFoundException(message: String) : DomainException(message)

/** A domain invariant or input rule was violated. Maps to HTTP 400. */
open class DomainValidationException(message: String) : DomainException(message)

/** The operation conflicts with the current state of the resource. Maps to HTTP 409. */
open class DomainConflictException(message: String) : DomainException(message)
