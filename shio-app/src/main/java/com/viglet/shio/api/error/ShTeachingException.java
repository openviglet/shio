/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.error;

import org.springframework.http.ProblemDetail;

/**
 * Base class for exceptions that carry their own RFC 9457 teaching problem (SH80).
 *
 * <p>{@link ShApiExceptionHandler} renders any {@code ShTeachingException} with the
 * status and body its {@link ShProblem} declares, so a new domain error is a
 * constructor — not another {@code @ExceptionHandler} method. The exception
 * message mirrors {@code detail} so logs and the wire agree.</p>
 *
 * <p>Subclasses keep their {@code @ResponseStatus} annotation where they already
 * had one: the annotation documents the intent and remains the fallback if this
 * advice is ever bypassed, while the {@code ShProblem} status is what actually
 * answers the request.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShTeachingException extends RuntimeException {

    private final transient ShProblem problem;

    protected ShTeachingException(ShProblem problem) {
        super(problem.toProblemDetail().getDetail() != null
                ? problem.toProblemDetail().getDetail()
                : problem.toProblemDetail().getTitle());
        this.problem = problem;
    }

    public ShProblem problem() {
        return problem;
    }

    public ProblemDetail toProblemDetail() {
        return problem.toProblemDetail();
    }
}
