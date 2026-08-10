/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.acl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ACL entry points carry their own transaction (SH225).
 *
 * <h2>The task's diagnosis was half wrong, and the wrong half is recorded rather than dropped</h2>
 *
 * <p>SH225 said the check depended on {@code spring.jpa.open-in-view} because {@code aclChain}
 * climbed a <b>lazy</b> {@code parentFolder}. It is not lazy: {@code ShFolder.parentFolder} is a
 * plain {@code @ManyToOne} and JPA's default there is <b>EAGER</b>, so the whole ancestor chain
 * arrived with the {@code findById}. A {@code @SpringBootTest} written to catch the predicted
 * {@code LazyInitializationException} — with {@code open-in-view} off and a three-level chain —
 * passed on the unfixed code, three tests green. It is not in this file because a test that is
 * green either way is not evidence, and it was spending one of the two remaining Spring context
 * slots to be green (see {@code ShContextCacheHeadroomTest}).</p>
 *
 * <p>What was real is the <b>dependency</b>, one level down from where the task put it. The check
 * worked because of an entity mapping's default fetch type, so the first person to do the ordinary
 * JPA hygiene thing and mark that association {@code LAZY} — for reasons with nothing to do with
 * permissions — would have broken <em>authorization</em>, and broken it as a 500 rather than as a
 * denial. Walking the chain by id makes the decision independent of the mapping. Guarding that
 * independence needs a lint rather than a fixture, which is SH650.</p>
 *
 * <h2>And this, which fails on the code the task replaced</h2>
 *
 * <p>{@code requireRead}/{@code requireWrite} carried no annotation and reached
 * {@code canRead}/{@code canWrite} by <b>self-invocation</b>, which never crosses the Spring proxy
 * — so the {@code @Transactional} on those methods never applied to the path callers actually take.
 * It costs no context to assert, and it is the assertion SH225 is closed with.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShContentAclEntryPointTest {

    @Test
    @DisplayName("SH225: the annotation is on the entry point, not only on the inner method")
    void theEntryPointIsTheTransactionalOne() throws Exception {
        for (String entryPoint : List.of("requireRead", "requireWrite")) {
            assertThat(ShContentAclService.class.getMethod(entryPoint, String.class)
                    .getAnnotation(Transactional.class))
                    .as("""
                            `%s` is the method callers hold, so it is the method the proxy sees.                             An inner @Transactional reached only by self-invocation is not a                             weaker guarantee than none — it is the same guarantee, spelled so                             that it looks like more (SH225).""".formatted(entryPoint))
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("SH225: and the inner methods keep theirs, because they are also called directly")
    void theInnerMethodsAreStillAnnotated() {
        // Not decoration once the entry points have their own: `canRead`/`canWrite` are public API
        // that callers hold directly (ShContentAclServiceTest is one), and on that path the proxy
        // does apply. Removing them would have made the entry-point fix look like a move rather
        // than an addition.
        for (String inner : List.of("canRead", "canWrite")) {
            assertThat(java.util.Arrays.stream(ShContentAclService.class.getMethods())
                    .filter(m -> m.getName().equals(inner))
                    .anyMatch(m -> m.getAnnotation(Transactional.class) != null))
                    .as("`%s` is reached directly as well as by self-invocation", inner)
                    .isTrue();
        }
    }
}
