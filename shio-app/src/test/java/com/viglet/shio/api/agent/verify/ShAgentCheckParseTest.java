/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH1120 — {@code all} is a token, so a caller can ask for every group and name one of them.
 *
 * <h2>The defect this closes, which was on the other side of the wire</h2>
 *
 * <p>{@code shio verify --delivery} ran <b>fewer</b> checks than {@code shio verify}. The CLI
 * composed the set itself — the literal {@code content,routes,diagnostics}, a hand-written copy of
 * {@link ShAgentCheck#defaults(boolean)} that had been missing {@code page} since SH516 and
 * {@code a11y} since it shipped. The flag asking for the stronger proof sent the narrower run, and
 * with fetching on it traded two groups for one that was <i>already</i> in the default set.</p>
 *
 * <p>The CLI could not express what the flag means without that list. A bare {@code all} resolves to
 * the defaults, which drop {@link ShAgentCheck#DELIVERY} where fetching is off — and the teaching
 * 400 with it, so a caller that asked to have its routes proved would get {@code ok} over nothing.
 * Naming {@code delivery} alone switches the network-free rules off. Neither is the request.</p>
 *
 * <p>{@code all,delivery} is, and this is what makes it parse: the same idiom a context section has
 * had since SH157, where {@code all,manifest} means "the usual pack plus this one". No test of this
 * enum's {@code parse} existed, which is why a list on the client side was the natural way to get
 * there.</p>
 *
 * <p>A plain unit test: {@code parse} is static and reads no context, so booting one would buy
 * nothing and cost the suite several seconds.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShAgentCheckParseTest {

    private static final boolean FETCHING_ON = true;
    private static final boolean FETCHING_OFF = false;

    @Test
    @DisplayName("SH1120: all as a token means every group, and naming one does not narrow the run")
    void allIsAToken() {
        assertThat(ShAgentCheck.parse("all,delivery", FETCHING_ON))
                .as("SH1120: this is the request `--delivery` means — every group that can run, "
                        + "plus delivery by name. The CLI wrote `content,routes,diagnostics,"
                        + "delivery` because it could not say this, and that list was missing "
                        + "`page` and `a11y`")
                .containsExactlyInAnyOrderElementsOf(ShAgentCheck.defaults(FETCHING_ON));

        assertThat(ShAgentCheck.parse("all,delivery", FETCHING_ON))
                .as("SH1120: including the two the hand-written copy dropped, which is the whole "
                        + "of the defect")
                .contains(ShAgentCheck.PAGE, ShAgentCheck.A11Y);

        assertThat(ShAgentCheck.parse("delivery,all", FETCHING_ON))
                .as("SH1120: and the order does not matter — a set, not a sequence")
                .containsExactlyInAnyOrderElementsOf(ShAgentCheck.defaults(FETCHING_ON));
    }

    @Test
    @DisplayName("SH1120: all,delivery with fetching off still answers the teaching 400")
    void namingDeliveryIsStillARequest() {
        // The property that rules out sending a bare `all`, and the reason this test exists beside
        // the one above rather than inside it. `defaults(false)` has no DELIVERY, so `all` alone
        // would come back as a quiet success over a proof that never ran — SH141 calls that the
        // most expensive answer this endpoint can give.
        assertThatThrownBy(() -> ShAgentCheck.parse("all,delivery", FETCHING_OFF))
                .as("SH1120/SH141: a caller that named `delivery` asked for it, whether or not it "
                        + "also said `all`. Silently dropping it is what the 400 exists to "
                        + "prevent, and `all` expanding to the defaults must not become a way "
                        + "around it")
                .isInstanceOf(ShAgentCheck.DeliveryDisabledException.class);

        Set<ShAgentCheck> bare = ShAgentCheck.parse("all", FETCHING_OFF);
        assertThat(bare)
                .as("SH1120: a bare `all` keeps its old meaning — the defaults, which cannot "
                        + "include a group this instance cannot run. The asymmetry is deliberate: "
                        + "`all` asks what is possible, naming a group asks for that group")
                .doesNotContain(ShAgentCheck.DELIVERY)
                .isEqualTo(ShAgentCheck.defaults(FETCHING_OFF));
    }

    @Test
    @DisplayName("SH1120: an unknown name is still a teaching 400, and all did not become a wildcard")
    void theVocabularyStillTeaches() {
        assertThatThrownBy(() -> ShAgentCheck.parse("all,nonsense", FETCHING_ON))
                .as("SH1120: `all` resolving early must not make the rest of the list unread — a "
                        + "caller with a typo beside it needs the same 400 it would get alone")
                .isInstanceOf(ShAgentCheck.UnknownCheckException.class);

        assertThat(ShAgentCheck.names())
                .as("SH1120: and `all` is not a group. That is what lets the CLI send "
                        + "`all,delivery` and still name no check group of its own — the word "
                        + "means `you compose it`, so it cannot be one of the things composed")
                .doesNotContain("all");
    }
}
