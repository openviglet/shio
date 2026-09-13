/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.staticfile.ShUploadPolicy.Verdict;

/**
 * SH951 — what this instance accepts, and what it cleans before accepting.
 *
 * <p>SH950 stopped uploaded bytes being <em>served</em> as documents. They were still stored, still
 * listed, and still copied into a content tree by {@code shio pull}, so the hole moved rather than
 * closed. This is the half that decides whether the bytes arrive.</p>
 *
 * <p>The HTTP boundary — an HTML upload refused with a teaching 400, a scripted SVG stored clean
 * and flagged — is in {@code ShStaticFileAPITest} (its {@code stage06} methods), beside the rest of
 * that route's assertions and inside a context that already exists.</p>
 */
class ShUploadPolicyTest {

    private static ShUploadPolicy policy() {
        return new ShUploadPolicy(new ShUploadPolicyProperties());
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The refusal itself, because {@code getMessage()} is only the problem's {@code detail} — and
     * the half worth asserting is the {@code fix} (P6: an error is an instruction).
     */
    private static ShTeachingException catchTeaching(Runnable call) {
        try {
            call.run();
        } catch (ShTeachingException e) {
            return e;
        }
        throw new AssertionError("SH951: expected the policy to refuse, and nothing was thrown");
    }

    @Test
    void sh951TheShippedDefaultsRefuseDocumentsAndCleanSvg() {
        ShUploadPolicy policy = policy();

        assertThat(policy.verdictFor("text/html")).isEqualTo(Verdict.DENY);
        assertThat(policy.verdictFor("application/xhtml+xml")).isEqualTo(Verdict.DENY);
        assertThat(policy.verdictFor("text/javascript")).isEqualTo(Verdict.DENY);
        assertThat(policy.verdictFor("application/javascript")).isEqualTo(Verdict.DENY);

        assertThat(policy.verdictFor("image/svg+xml"))
                .as("SH951: denying SVG outright would refuse a replicated site's logo, which is "
                        + "why there is a third verdict at all")
                .isEqualTo(Verdict.SANITIZE);

        // A policy is a list of exceptions: everything else arrives.
        assertThat(policy.verdictFor("image/png")).isEqualTo(Verdict.ALLOW);
        assertThat(policy.verdictFor("text/css")).isEqualTo(Verdict.ALLOW);
        assertThat(policy.verdictFor("font/woff2")).isEqualTo(Verdict.ALLOW);
        assertThat(policy.verdictFor("application/pdf")).isEqualTo(Verdict.ALLOW);

        // Parameters must not defeat the lookup: `text/html; charset=utf-8` is what a probe returns.
        assertThat(policy.verdictFor("text/html; charset=utf-8")).isEqualTo(Verdict.DENY);
        assertThat(policy.verdictFor("TEXT/HTML")).isEqualTo(Verdict.DENY);
        assertThat(policy.verdictFor(null)).isEqualTo(Verdict.ALLOW);
    }

    /**
     * The default that had to be settled rather than assumed, and the reason it is written down:
     * an ordinary replication uploads no JavaScript, but {@code shio clone --scripts} does.
     */
    @Test
    void sh951RefusingJavascriptNamesThePropertyThatWouldAllowIt() {
        ShTeachingException refusal = catchTeaching(() -> policy().check("text/javascript", "bundle.js"));

        assertThat(refusal).isInstanceOf(ShUploadPolicy.RefusedUploadException.class);
        assertThat(refusal.problem().status().value()).isEqualTo(400);

        // The remedy, not just the refusal (P6). `shio clone --scripts` uploads a captured site's
        // own bundle on purpose, and nothing in the suites drives that through a live upload — so
        // a refusal that does not carry its own fix breaks the feature as a mystery.
        assertThat(refusal.toProblemDetail().getProperties())
                .extractingByKey("fix").asString()
                .contains("shio.upload.policy.text/javascript=allow")
                .contains("--scripts");
        assertThat(refusal.toProblemDetail().getProperties())
                .extractingByKey("allowed").asString()
                .contains("allow", "deny", "sanitize");
    }

    @Test
    void sh951APolicyCanBeWidenedAndNarrowedByConfiguration() {
        ShUploadPolicyProperties properties = new ShUploadPolicyProperties();
        properties.getPolicy().put("text/javascript", "allow");
        properties.getPolicy().put("image/*", "deny");
        ShUploadPolicy policy = new ShUploadPolicy(properties);

        assertThat(policy.verdictFor("text/javascript")).isEqualTo(Verdict.ALLOW);
        assertThat(policy.verdictFor("image/png"))
                .as("SH951: a family key covers the whole family")
                .isEqualTo(Verdict.DENY);
        assertThat(policy.verdictFor("image/svg+xml"))
                .as("SH951: an exact key beats its family, or narrowing one type would be "
                        + "undone by a rule about all of them")
                .isEqualTo(Verdict.SANITIZE);
    }

    @Test
    void sh951AnUnreadablePolicySaysSoInsteadOfGuessing() {
        ShUploadPolicyProperties properties = new ShUploadPolicyProperties();
        properties.getPolicy().put("image/png", "maybe");

        ShTeachingException broken =
                catchTeaching(() -> new ShUploadPolicy(properties).verdictFor("image/png"));

        assertThat(broken).isInstanceOf(ShUploadPolicy.UnreadablePolicyException.class);
        assertThat(broken.problem().title())
                .as("SH951: a misconfigured instance must name the key and the value it could not read")
                .contains("image/png", "maybe");
        assertThat(broken.toProblemDetail().getProperties())
                .extractingByKey("fix").asString()
                .contains("shio.upload.policy.image/png");
    }

    @Test
    void sh951TheSanitizerTakesTheBehaviourOutAndLeavesTheDrawing() {
        ShSvgSanitizer sanitizer = new ShSvgSanitizer();

        String hostile = """
                <?xml version="1.0"?>
                <!DOCTYPE svg SYSTEM "http://example.test/evil.dtd">
                <?xml-stylesheet href="http://example.test/x.css" type="text/css"?>
                <svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)" viewBox="0 0 10 10">
                  <script>alert(document.domain)</script>
                  <script/>
                  <foreignObject><body xmlns="http://www.w3.org/1999/xhtml">hi</body></foreignObject>
                  <a xlink:href="javascript:alert(1)"><circle cx="5" cy="5" r="4" fill="red"/></a>
                  <image href="data:image/png;base64,iVBOR"/>
                  <rect width="10" height="10" onclick='alert(2)' fill="blue"/>
                </svg>
                """;

        ShSvgSanitizer.Result result = sanitizer.sanitize(utf8(hostile));
        String clean = new String(result.bytes(), StandardCharsets.UTF_8);

        assertThat(result.changed()).isTrue();
        assertThat(clean)
                .as("SH951: nothing that runs may survive")
                .doesNotContain("<script", "alert(", "foreignObject", "onload", "onclick",
                        "javascript:", "<!DOCTYPE", "xml-stylesheet");

        assertThat(clean)
                .as("SH951: the drawing is the point — a sanitizer that empties the picture is a "
                        + "denial with extra steps")
                .contains("<svg", "circle", "fill=\"red\"", "<rect", "fill=\"blue\"", "viewBox");
        assertThat(clean)
                .as("SH951: a data: image is not a script and must survive — an inline-image SVG "
                        + "is exactly what a capture produces")
                .contains("data:image/png;base64");
    }

    @Test
    void sh951AnHonestSvgIsReturnedUntouched() {
        byte[] logo = utf8("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">"
                + "<path d=\"M0 0h10v10H0z\" fill=\"#123456\"/></svg>");

        ShSvgSanitizer.Result result = new ShSvgSanitizer().sanitize(logo);

        assertThat(result.changed())
                .as("SH951: a clean SVG must come back byte-identical, or every replication digest "
                        + "moves and `shio diff` reports a change nobody made")
                .isFalse();
        assertThat(result.bytes()).isEqualTo(logo);
    }

    /** The control: the sanitizer would be useless if it reported no removals on hostile input. */
    @Test
    void sh951TheSanitizerNamesWhatItRemoved() {
        ShSvgSanitizer.Result result = new ShSvgSanitizer()
                .sanitize(utf8("<svg onload=\"x()\"><script>y()</script></svg>"));

        assertThat(result.removed()).contains("script", "event-handler attribute");
    }
}
