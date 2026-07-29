/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentResolved;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH79 — address resolution against real content.
 *
 * <p>Fixture shape is chosen to exercise the one genuinely hard case: a post whose
 * friendly URL is <em>also</em> a folder path, which is what makes a single guessable
 * path namespace unworkable and the lenient {@code site:} form ambiguous.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAddressResolverTest {

    @Autowired
    private ShAddressResolver resolver;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String siteId;
    private String rootFolderId;
    private String blogFolderId;
    private String postId;
    private String postTypeName;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "addr-site-" + UUID.randomUUID();
            site.setName(siteName);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = folder("root", (byte) 1, null, site);
            rootFolderId = root.getId();
            ShFolder blog = folder("blog", (byte) 0, root, site);
            blogFolderId = blog.getId();
            ShFolder y2026 = folder("2026", (byte) 0, blog, site);

            ShPostType postType = new ShPostType();
            postTypeName = "Addr" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Addr");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            postId = post(site, y2026, postType, "Hello World", "/blog/hello-world",
                    ShPostState.DRAFT);
            // A post whose furl collides with the /blog folder path — the ambiguity case.
            post(site, root, postType, "Collides", "/blog", ShPostState.PUBLISHED);
        });
    }

    private ShFolder folder(String name, byte root, ShFolder parent, ShSite site) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder(root);
        folder.setParentFolder(parent);
        folder.setShSite(site);
        folder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private String post(ShSite site, ShFolder folder, ShPostType type, String title,
                        String furl, ShPostState state) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData("{}");
        entityManager.persist(post);
        return post.getId();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SH321: two posts on one URL is a teaching 409, not a 500")
    void aContestedUrlIsRefused() {
        // Before SH321 the derived Optional finder threw IncorrectResultSizeDataAccessException
        // here, so a defect the instance already reports as `duplicate-url` (an SH99 error since
        // SH100) reached the caller as a 500 — the server knowing exactly what happened and
        // answering with the least useful thing available.
        String contested = "/blog/contested";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = entityManager.find(ShSite.class, siteId);
            ShFolder root = entityManager.find(ShFolder.class, rootFolderId);
            ShPostType type = entityManager
                    .createQuery("select t from ShPostType t where t.name = :n", ShPostType.class)
                    .setParameter("n", postTypeName)
                    .getSingleResult();
            post(site, root, type, "First", contested, ShPostState.DRAFT);
            post(site, root, type, "Second", contested, ShPostState.DRAFT);
        });

        assertThatThrownBy(() -> resolver.resolve("post:" + siteName + contested))
                .isInstanceOf(ShAddressResolver.DuplicateUrlException.class)
                .satisfies(failure -> {
                    var problem = ((ShAddressResolver.DuplicateUrlException) failure)
                            .problem().toProblemDetail();
                    assertThat(problem.getStatus()).isEqualTo(409);
                    assertThat(problem.getProperties().get("allowed").toString())
                            .as("both posts by `id:` address — the one form that resolves for "
                                    + "either of them, since the `post:` address is precisely what "
                                    + "is ambiguous (SH172's fallback, met from the other side)")
                            .contains("id:");
                    assertThat(problem.getProperties().get("fix").toString())
                            .contains("post.move")
                            .contains("verify");
                });
    }

    @Test
    void resolvesAPostByItsFriendlyUrl() {
        ShAgentResolved resolved = resolver.resolve("post:" + siteName + "/blog/hello-world");

        assertThat(resolved.kind()).isEqualTo("post");
        assertThat(resolved.id()).isEqualTo(postId);
        assertThat(resolved.title()).isEqualTo("Hello World");
        assertThat(resolved.type()).isEqualTo(postTypeName);
        assertThat(resolved.state()).isEqualTo("DRAFT");
        assertThat(resolved.address()).isEqualTo("post:" + siteName + "/blog/hello-world");
    }

    @Test
    void resolvesAFolderByItsNameChain() {
        assertThat(resolver.resolve("folder:" + siteName + "/blog").id()).isEqualTo(blogFolderId);
        assertThat(resolver.resolve("folder:" + siteName + "/blog/2026").kind()).isEqualTo("folder");
        // The site root is the empty chain.
        assertThat(resolver.resolve("folder:" + siteName + "/").id()).isEqualTo(rootFolderId);
    }

    @Test
    void resolvesASiteAndReportsItsRootFolder() {
        ShAgentResolved resolved = resolver.resolve("site:" + siteName);

        assertThat(resolved.kind()).isEqualTo("site");
        assertThat(resolved.id()).isEqualTo(siteId);
        assertThat(resolved.parent()).isEqualTo(rootFolderId);
    }

    @Test
    void resolvesBySiteIdAsWellAsName() {
        assertThat(resolver.resolve("post:" + siteId + "/blog/hello-world").id()).isEqualTo(postId);
    }

    @Test
    void siteNameMatchingIsCaseInsensitive() {
        assertThat(resolver.resolve("folder:" + siteName.toUpperCase() + "/BLOG").id())
                .isEqualTo(blogFolderId);
    }

    @Test
    void idSchemeResolvesAnyKindAndReturnsTheCanonicalAddress() {
        // Ids stay valid forever; `id:` is how you say one deliberately — and the reply
        // teaches the address that replaces it.
        assertThat(resolver.resolve("id:" + postId).address())
                .isEqualTo("post:" + siteName + "/blog/hello-world");
        assertThat(resolver.resolve("id:" + blogFolderId).address())
                .isEqualTo("folder:" + siteName + "/blog");
        assertThat(resolver.resolve("id:" + siteId).kind()).isEqualTo("site");
    }

    @Test
    void lenientSiteFormPrefersAPostWhenOnlyAPostIsThere() {
        assertThat(resolver.resolve("site:" + siteName + "/blog/hello-world").kind())
                .isEqualTo("post");
    }

    @Test
    void lenientSiteFormFallsBackToAFolder() {
        assertThat(resolver.resolve("site:" + siteName + "/blog/2026").kind()).isEqualTo("folder");
    }

    @Test
    void lenientSiteFormRefusesToGuessWhenBothExist() {
        // /blog is both a folder path and a post's furl in this fixture. Picking one
        // would be a coin flip whose consequence only shows up as wrong content later.
        assertThatThrownBy(() -> resolver.resolve("site:" + siteName + "/blog"))
                .isInstanceOf(ShAddressResolver.AmbiguousAddressException.class)
                .satisfies(thrown -> {
                    var properties = ((ShAddressResolver.AmbiguousAddressException) thrown)
                            .toProblemDetail().getProperties();
                    assertThat(properties.get("allowed").toString())
                            .contains("post:" + siteName + "/blog")
                            .contains("folder:" + siteName + "/blog");
                    assertThat(properties.get("fix").toString()).contains("post:");
                });
        // The explicit forms still work — the ambiguity is in the question, not the data.
        assertThat(resolver.resolve("post:" + siteName + "/blog").kind()).isEqualTo("post");
        assertThat(resolver.resolve("folder:" + siteName + "/blog").kind()).isEqualTo("folder");
    }

    @Test
    void anUnresolvedAddressListsWhatIsThere() {
        assertThatThrownBy(() -> resolver.resolve("folder:" + siteName + "/nosuchfolder"))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class)
                .satisfies(thrown -> assertThat(((ShAddressResolver.UnresolvedAddressException) thrown)
                        .toProblemDetail().getProperties().get("allowed").toString())
                        .contains("/blog"));
    }

    @Test
    void aTypoInAPathGetsANearestMatch() {
        assertThatThrownBy(() -> resolver.resolve("folder:" + siteName + "/blg"))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class)
                .satisfies(thrown -> assertThat(((ShAddressResolver.UnresolvedAddressException) thrown)
                        .toProblemDetail().getProperties())
                        .containsEntry("didYouMean", "/blog"));
    }

    @Test
    void findReturnsEmptyInsteadOfThrowingForCallersThatExpectAbsence() {
        assertThat(resolver.find(ShAddress.parse("post:" + siteName + "/nope"))).isEmpty();
        assertThat(resolver.find(ShAddress.parse("post:" + siteName + "/blog/hello-world")))
                .isPresent();
    }

    @Test
    void anUnknownSiteDoesNotResolve() {
        assertThatThrownBy(() -> resolver.resolve("post:no-such-site/anything"))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class);
    }

    @Test
    void addressOfIsTheInverseOfResolve() {
        // The round trip is what lets the context pack stay id-free later (SH121):
        // whatever the pack shows must be addressable, and whatever resolves must
        // print back the same way.
        ShAgentResolved resolved = resolver.resolve("post:" + siteName + "/blog/hello-world");

        assertThat(resolver.resolve(resolved.address()).id()).isEqualTo(resolved.id());
    }
}
