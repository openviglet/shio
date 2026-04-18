/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.post.unified;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link ShPostUnified}: {@code (id, state)}.
 */
public class ShPostUnifiedId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private ShPostState state;

    public ShPostUnifiedId() {
    }

    public ShPostUnifiedId(String id, ShPostState state) {
        this.id = id;
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ShPostState getState() {
        return state;
    }

    public void setState(ShPostState state) {
        this.state = state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShPostUnifiedId that)) return false;
        return Objects.equals(id, that.id) && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, state);
    }
}
