/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Content lint (SH99) and route verification (SH100) — the agent's own correctness
 * check.
 *
 * <h2>Why this is the cheapest thing in Block K</h2>
 *
 * <p>Two of the three human round trips in a typical build exist because the agent
 * cannot see its own work. This removes the first one: it is the content-side
 * equivalent of {@code tsc}, and like {@code tsc} its value is that it is run
 * <em>before</em> anyone is asked to look. Everything it reports is a fact already in
 * the database — no rendering, no network, no delivery layer — so it costs one read
 * pass and answers in the same call.</p>
 *
 * <h2>The severity rule, stated once</h2>
 *
 * <p>An <b>error</b> is broken from a visitor's point of view: a published page with a
 * required field empty, a reference to a post that does not exist, an image whose bytes
 * are gone, two pages claiming one URL. A <b>warning</b> is something this process
 * cannot prove from inside the CMS — an empty folder may be a section still being
 * built, and a site-relative link may be a front-end route that Shio never sees. That
 * line is what makes {@code ok} worth branching on: if warnings failed builds, callers
 * would learn to ignore the exit code, and then the errors would go unread too.</p>
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p>It does not fetch anything. "Internal links return 200" in §IX.2 is answered
 * against the content graph — the set of published friendly URLs, using the same
 * leading-slash tolerance the CDA's by-url lookup applies — rather than by crawling a
 * front end that may not be running, may be a different deployment, and may legitimately
 * own routes the CMS has never heard of. Fetching a page is SH101's job, where it is
 * explicit, opt-in and about one URL at a time.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShAgentVerifyService {

    private static final List<ShPostState> BOTH_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    /** Widgets whose stored value is another post's id. */
    private static final Set<String> REFERENCE_WIDGETS =
            Set.of(ShSystemWidget.CONTENT_SELECT, ShSystemWidget.FILE);

    /** Widgets whose stored value is markup worth scanning for links and images. */
    private static final Set<String> MARKUP_WIDGETS =
            Set.of(ShSystemWidget.HTML_EDITOR, ShSystemWidget.ACE_HTML);

    /** Widgets that hold no value of their own — never "required". */
    private static final Set<String> CONTAINER_WIDGETS =
            Set.of(ShSystemWidget.TAB, ShSystemWidget.RELATOR);

    /** The public path the SH38 subsystem serves stored bytes on. */
    private static final String FILE_SOURCE_PREFIX = "/file_source/";

    private final ShAgentSiteAccess siteAccess;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShFileStorageService fileStorage;
    private final ShAgentIncidentLog incidentLog;
    private final ObjectMapper objectMapper;
    private final ShAgentProperties properties;

    public ShAgentVerifyService(ShAgentSiteAccess siteAccess,
                                ShFolderRepository shFolderRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShFileStorageService fileStorage,
                                ShAgentIncidentLog incidentLog,
                                ObjectMapper objectMapper,
                                ShAgentProperties properties) {
        this.siteAccess = siteAccess;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.fileStorage = fileStorage;
        this.incidentLog = incidentLog;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Runs the requested checks.
     *
     * @param site   site id, name or {@code site:} address; {@code null} = every
     *               readable site
     * @param checks comma-separated {@link ShAgentCheck} groups; {@code null} = all
     * @param limit  findings to return; {@code null} = configured default. The report
     *               always counts what it produced, so a cut is visible
     */
    public ShAgentReport verify(String site, String checks, Integer limit) {
        Set<ShAgentCheck> requested = ShAgentCheck.parse(checks);
        int cap = limit == null || limit <= 0
                ? properties.getVerify().getDefaultLimit()
                : Math.min(limit, properties.getVerify().getMaxLimit());

        List<ShSite> sites = siteAccess.readable(site);
        Scan scan = scan(sites);
        List<Finding> findings = new ArrayList<>();

        if (requested.contains(ShAgentCheck.CONTENT)) {
            content(scan, findings);
        }
        if (requested.contains(ShAgentCheck.ROUTES)) {
            routes(scan, findings);
        }
        if (requested.contains(ShAgentCheck.DIAGNOSTICS)) {
            diagnostics(findings);
        }

        List<Finding> ordered = findings.stream().sorted(ORDER).toList();
        int errors = (int) ordered.stream().filter(Finding::isError).count();
        List<Finding> capped = ordered.size() > cap ? ordered.subList(0, cap) : ordered;
        return new ShAgentReport(
                errors == 0,
                requested.stream().sorted().map(ShAgentCheck::wireName).toList(),
                scan.checked(),
                errors,
                ordered.size() - errors,
                ordered.size(),
                ordered.size() > cap,
                List.copyOf(capped));
    }

    /**
     * Deterministic finding order: check group, then errors before warnings, then rule,
     * address and field. Two runs over unchanged content produce byte-identical reports
     * (P5), which is what makes a report diffable in a PR.
     */
    private static final Comparator<Finding> ORDER = Comparator
            .comparing(Finding::check, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(finding -> finding.isError() ? 0 : 1)
            .thenComparing(Finding::rule, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Finding::address, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Finding::field, Comparator.nullsLast(Comparator.naturalOrder()));

    // --- the read pass -------------------------------------------------------

    /**
     * One pass over everything the checks need, so no rule re-queries.
     *
     * <p>Both states are loaded rather than draft-preferred rows, because two rules turn
     * on the difference: a duplicate URL is only a live defect between {@code PUBLISHED}
     * rows, and "published depends on unpublished" cannot be asked at all without
     * knowing which ids have a published row.</p>
     */
    private Scan scan(List<ShSite> sites) {
        Map<String, ShSite> siteById = new LinkedHashMap<>();
        List<ShPostUnified> rows = new ArrayList<>();
        for (ShSite each : sites) {
            siteById.put(each.getId(), each);
            rows.addAll(shPostUnifiedRepository.findByShSite_IdAndStateIn(each.getId(), BOTH_STATES));
        }
        // Site-less posts are nobody's rows, which is exactly why they need asking for.
        rows.addAll(shPostUnifiedRepository.findWithoutSite(BOTH_STATES));

        Set<String> allIds = new HashSet<>();
        Set<String> publishedIds = new HashSet<>();
        Map<String, ShPostUnified> draftPreferred = new LinkedHashMap<>();
        for (ShPostUnified row : rows) {
            allIds.add(row.getId());
            if (row.getState() == ShPostState.PUBLISHED) {
                publishedIds.add(row.getId());
            }
            ShPostUnified kept = draftPreferred.get(row.getId());
            if (kept == null || row.getState() == ShPostState.DRAFT) {
                draftPreferred.put(row.getId(), row);
            }
        }
        List<ShPostUnified> published = rows.stream()
                .filter(row -> row.getState() == ShPostState.PUBLISHED)
                .sorted(Comparator.comparing(ShPostUnified::getId))
                .toList();
        List<ShPostUnified> posts = draftPreferred.values().stream()
                .sorted(Comparator.comparing(ShPostUnified::getId))
                .toList();
        return new Scan(siteById, posts, published, allIds, publishedIds, folders(siteById.keySet()));
    }

    /**
     * Every folder whose owning site is readable, plus the true orphans.
     *
     * <p>The site is resolved with {@link ShFolderSite} — only a root folder carries
     * {@code shSite} (SH128), so a direct read would classify every nested folder as an
     * orphan and bury the report in false errors.</p>
     */
    private List<Scanned> folders(Set<String> readableSiteIds) {
        List<Scanned> scanned = new ArrayList<>();
        for (ShFolder folder : shFolderRepository.findAll()) {
            ShSite owner = ShFolderSite.of(folder);
            if (owner == null) {
                scanned.add(new Scanned(folder, null));
            } else if (readableSiteIds.contains(owner.getId())) {
                scanned.add(new Scanned(folder, owner));
            }
        }
        return scanned.stream().sorted(Comparator.comparing(entry -> entry.folder().getId())).toList();
    }

    // --- SH99: content ------------------------------------------------------

    private void content(Scan scan, List<Finding> findings) {
        String check = ShAgentCheck.CONTENT.wireName();
        for (ShPostUnified post : scan.posts()) {
            String address = address(post);
            if (post.getShSite() == null) {
                findings.add(Finding.error(check, "orphan-post", address, null,
                        "This post belongs to no site, so nothing can reach it — not the "
                                + "console, not the CDA, not an address.",
                        "Move it into a site with a post.move op (POST /api/v2/agent/batch), "
                                + "or delete it if it was created by mistake."));
                continue;
            }
            JsonNode data = data(post);
            for (ShPostTypeAttr attr : declared(post.getShPostType())) {
                fieldFindings(check, post, address, data, attr, scan, findings);
            }
        }
        duplicateUrls(check, scan, findings);
        folderFindings(check, scan, findings);
    }

    private void fieldFindings(String check, ShPostUnified post, String address, JsonNode data,
                               ShPostTypeAttr attr, Scan scan, List<Finding> findings) {
        String widget = widgetName(attr);
        String field = attr.getName();
        if (field == null || field.isBlank()) {
            return;
        }
        JsonNode value = data.get(field);

        if (attr.getRequired() == 1 && !CONTAINER_WIDGETS.contains(widget) && isEmpty(value)) {
            findings.add(Finding.error(check, "required-field-empty", address, field,
                    "Required field '" + field + "' is empty.",
                    "Set it with a post.upsert op on POST /api/v2/agent/batch, or drop the "
                            + "required flag on the post-type if it is genuinely optional."));
        }
        if (!REFERENCE_WIDGETS.contains(widget) || isEmpty(value) || !value.isString()) {
            return;
        }
        String targetId = value.asString().trim();
        if (!scan.allIds().contains(targetId) && !existsPost(targetId)) {
            findings.add(Finding.error(check, "dangling-reference", address, field,
                    "Field '" + field + "' points at post " + targetId + ", which does not exist.",
                    "Point it at a post from GET /api/v2/agent/find, or clear the field."));
            return;
        }
        if (ShSystemWidget.FILE.equals(widget)) {
            missingAsset(check, address, field, targetId, findings);
        }
        // A published page whose reference has no published row renders a hole for a
        // visitor while looking perfectly fine to the editor, who sees both drafts.
        if (scan.publishedIds().contains(post.getId()) && !scan.publishedIds().contains(targetId)
                && !targetId.equals(post.getId())) {
            findings.add(Finding.error(check, "unpublished-dependency", address, field,
                    "This post is published but field '" + field + "' points at a post that is not.",
                    "Publish the referenced post (shio_publish / POST /api/v2/agent/batch with "
                            + "post.publish), or unpublish this one until it is ready."));
        }
    }

    private void missingAsset(String check, String address, String field, String fileId,
                              List<Finding> findings) {
        Optional<ShPostUnified> file = shPostUnifiedRepository.findById(fileId).stream()
                .min(Comparator.comparing(row -> row.getState() == ShPostState.PUBLISHED ? 0 : 1));
        String fileName = file.map(this::fileNameOf).orElse(null);
        if (fileName == null || !fileStorage.exists(fileId, fileName)) {
            findings.add(Finding.error(check, "missing-asset", address, field,
                    "Field '" + field + "' references file post " + fileId
                            + ", but its bytes are not in file storage"
                            + (fileName == null ? "" : " (" + fileName + ")") + ".",
                    "Re-upload the binary with POST /api/v2/staticfile/upload, or clear the field."));
        }
    }

    /**
     * Two published posts claiming one URL. Only published rows count: a draft sharing a
     * URL with the live page it will replace is the normal editing state, and flagging it
     * would fire on every single edit.
     */
    private void duplicateUrls(String check, Scan scan, List<Finding> findings) {
        Map<String, List<ShPostUnified>> byUrl = new LinkedHashMap<>();
        for (ShPostUnified post : scan.published()) {
            if (post.getShSite() == null || isBlank(post.getFurl())) {
                continue;
            }
            String key = post.getShSite().getId() + " "
                    + ShAddress.normalizePath(post.getFurl()).toLowerCase(java.util.Locale.ROOT);
            byUrl.computeIfAbsent(key, ignored -> new ArrayList<>()).add(post);
        }
        byUrl.values().stream().filter(group -> group.size() > 1).forEach(group -> {
            String others = group.stream().map(ShPostUnified::getId).sorted()
                    .reduce((first, second) -> first + ", " + second).orElse("");
            for (ShPostUnified post : group) {
                findings.add(Finding.error(check, "duplicate-url", address(post), null,
                        "Published URL '" + post.getFurl() + "' is claimed by "
                                + group.size() + " posts (" + others + ") — which one a "
                                + "visitor gets is undefined.",
                        "Give one of them a different URL with a post.move op "
                                + "(POST /api/v2/agent/batch), or unpublish the duplicate."));
            }
        });
    }

    private void folderFindings(String check, Scan scan, List<Finding> findings) {
        Set<String> foldersWithPosts = new HashSet<>();
        for (ShPostUnified post : scan.posts()) {
            if (post.getShFolder() != null) {
                foldersWithPosts.add(post.getShFolder().getId());
            }
        }
        Set<String> parents = new HashSet<>();
        for (Scanned entry : scan.folders()) {
            if (entry.folder().getParentFolder() != null) {
                parents.add(entry.folder().getParentFolder().getId());
            }
        }
        for (Scanned entry : scan.folders()) {
            ShFolder folder = entry.folder();
            if (entry.site() == null) {
                findings.add(Finding.error(check, "orphan-folder", "id:" + folder.getId(), null,
                        "Folder '" + folder.getName() + "' is not under any site, so it has no "
                                + "address and nothing inside it can be delivered.",
                        "Delete it, or recreate the content under a site with folder.upsert "
                                + "(POST /api/v2/agent/batch)."));
                continue;
            }
            boolean isRoot = folder.getRootFolder() == 1;
            if (!isRoot && !parents.contains(folder.getId()) && !foldersWithPosts.contains(folder.getId())) {
                findings.add(Finding.warning(check, "empty-folder", folderAddress(entry), null,
                        "Folder '" + folder.getName() + "' holds no posts and no subfolders.",
                        "Add the content it was created for, or delete it with a folder.delete "
                                + "op. A section that renders empty is the most common "
                                + "\"looks done but isn't\"."));
            }
        }
    }

    // --- SH100: routes ------------------------------------------------------

    private void routes(Scan scan, List<Finding> findings) {
        String check = ShAgentCheck.ROUTES.wireName();
        Map<String, Set<String>> publishedUrls = new HashMap<>();
        for (ShPostUnified post : scan.published()) {
            if (post.getShSite() != null && !isBlank(post.getFurl())) {
                publishedUrls.computeIfAbsent(post.getShSite().getId(), ignored -> new HashSet<>())
                        .add(ShAddress.normalizePath(post.getFurl()).toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (ShPostUnified post : scan.published()) {
            String address = address(post);
            if (isBlank(post.getFurl())) {
                findings.add(Finding.error(check, "url-missing", address, null,
                        "This post is published but has no friendly URL, so no route reaches it.",
                        "Give it one with a post.move op (POST /api/v2/agent/batch): "
                                + "{\"op\":\"post.move\",\"address\":\"id:" + post.getId()
                                + "\",\"to\":\"post:<site>/<url>\"}."));
            }
            markup(check, post, address, publishedUrls, findings);
        }
    }

    /**
     * Scans a published post's rich text for links and images.
     *
     * <p>Only published rows, and only the two markup widgets: a draft's broken link is
     * work in progress, and a plain-text field containing something that looks like a
     * path is not a link.</p>
     */
    private void markup(String check, ShPostUnified post, String address,
                        Map<String, Set<String>> publishedUrls, List<Finding> findings) {
        JsonNode data = data(post);
        Set<String> siteUrls = post.getShSite() == null
                ? Set.of()
                : publishedUrls.getOrDefault(post.getShSite().getId(), Set.of());
        for (ShPostTypeAttr attr : declared(post.getShPostType())) {
            if (!MARKUP_WIDGETS.contains(widgetName(attr))) {
                continue;
            }
            JsonNode value = data.get(attr.getName());
            if (isEmpty(value) || !value.isString()) {
                continue;
            }
            ShHtmlOutline.Outline outline = ShHtmlOutline.of(value.asString());
            for (ShHtmlOutline.Link link : outline.links()) {
                linkFinding(check, address, attr.getName(), link.href(), siteUrls, findings);
            }
            for (ShHtmlOutline.Image image : outline.images()) {
                imageFindings(check, address, attr.getName(), image, findings);
            }
        }
    }

    private void linkFinding(String check, String address, String field, String href,
                             Set<String> siteUrls, List<Finding> findings) {
        if (!isInternal(href)) {
            return;
        }
        String path = ShAddress.normalizePath(stripQuery(href)).toLowerCase(java.util.Locale.ROOT);
        if (path.startsWith(FILE_SOURCE_PREFIX) || siteUrls.contains(path) || "/".equals(path)) {
            return;
        }
        // A warning, not an error: the front end owns routes the CMS never sees (a
        // listing page, a generated tag index). Reporting those as errors would make the
        // exit code useless on any real site, which costs more than the missed defect.
        findings.add(Finding.warning(check, "link-unresolved", address, field,
                "Link '" + href + "' matches no published URL in this site.",
                "Publish the target, fix the link, or ignore this if the path is a "
                        + "front-end route rather than a Shio page."));
    }

    private void imageFindings(String check, String address, String field,
                               ShHtmlOutline.Image image, List<Finding> findings) {
        String src = image.src();
        if (image.alt() == null && isInternal(src)) {
            findings.add(Finding.warning(check, "image-no-alt", address, field,
                    "Image '" + src + "' has no alt attribute.",
                    "Add alt text, or alt=\"\" if the image is decorative — an absent "
                            + "attribute and an empty one mean different things to a screen reader."));
        }
        if (!isInternal(src) || !src.startsWith(FILE_SOURCE_PREFIX)) {
            return;
        }
        String[] parts = src.substring(FILE_SOURCE_PREFIX.length()).split("/", 2);
        if (parts.length == 2 && !fileStorage.exists(parts[0], decodePath(parts[1]))) {
            findings.add(Finding.error(check, "asset-missing", address, field,
                    "Image '" + src + "' has no bytes in file storage — a visitor gets a 404.",
                    "Re-upload it with POST /api/v2/staticfile/upload and point the "
                            + "markup at the returned post's URL."));
        }
    }

    // --- SH103: recent asynchronous failures --------------------------------

    /**
     * The incident log as findings.
     *
     * <p>A failed webhook is a defect in the same sense a broken link is — the site is
     * not what the content says it is — so it belongs in the report an agent already
     * reads rather than in a second call it has to remember to make. The detailed view
     * (first/last seen, counts) stays on {@code GET /api/v2/agent/diagnostics}.</p>
     */
    private void diagnostics(List<Finding> findings) {
        String check = ShAgentCheck.DIAGNOSTICS.wireName();
        for (ShAgentIncidentLog.Incident incident : incidentLog.recent(0)) {
            String repeats = incident.count() > 1 ? " (x" + incident.count() + ")" : "";
            findings.add(new Finding(check, incident.kind().wireName(),
                    incident.kind() == ShAgentIncidentLog.Kind.TRANSFORM
                            ? ShAgentReport.WARNING : ShAgentReport.ERROR,
                    null,
                    incident.subject(),
                    incident.message() + repeats,
                    incident.fix() != null ? incident.fix()
                            : "See GET /api/v2/agent/diagnostics for when it started and how often."));
        }
    }

    // --- helpers ------------------------------------------------------------

    /** Top-level attributes of a post-type, ordinal-ordered so findings are stable. */
    private static List<ShPostTypeAttr> declared(ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * Whether a referenced id is a post at all.
     *
     * <p>Asked only for ids the scan did not already see, because a reference may
     * legitimately point into a site this credential cannot read — and calling that
     * dangling would be a lie told by the allow-list rather than by the content.</p>
     */
    private boolean existsPost(String postId) {
        return !shPostUnifiedRepository.findById(postId).isEmpty();
    }

    private static String widgetName(ShPostTypeAttr attr) {
        return attr.getShWidget() == null || attr.getShWidget().getName() == null
                ? "" : attr.getShWidget().getName();
    }

    private JsonNode data(ShPostUnified post) {
        String json = post.getData();
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (JacksonException e) {
            return objectMapper.createObjectNode();
        }
    }

    private String fileNameOf(ShPostUnified file) {
        JsonNode data = data(file);
        JsonNode name = data.get(ShSystemWidget.FILE.toUpperCase(java.util.Locale.ROOT));
        if (name != null && name.isString() && !name.asString().isBlank()) {
            return name.asString().trim();
        }
        return isBlank(file.getTitle()) ? null : file.getTitle().trim();
    }

    private String address(ShPostUnified post) {
        if (post.getShSite() == null || isBlank(post.getFurl())) {
            return "id:" + post.getId();
        }
        return ShAddress.post(post.getShSite().getName(), post.getFurl()).toString();
    }

    private static String folderAddress(Scanned entry) {
        List<String> names = new ArrayList<>();
        for (ShFolder current = entry.folder(); current != null; current = current.getParentFolder()) {
            if (current.getRootFolder() == 1) {
                break;
            }
            names.add(0, current.getName() == null ? "" : current.getName());
        }
        return ShAddress.folder(entry.site().getName(), "/" + String.join("/", names)).toString();
    }

    /** Site-relative: a path this instance could plausibly own. Not {@code //host}. */
    private static boolean isInternal(String href) {
        return href != null && href.startsWith("/") && !href.startsWith("//");
    }

    private static String stripQuery(String href) {
        int cut = href.length();
        int query = href.indexOf('?');
        int fragment = href.indexOf('#');
        if (query >= 0) {
            cut = query;
        }
        if (fragment >= 0 && fragment < cut) {
            cut = fragment;
        }
        return href.substring(0, cut);
    }

    /** A stored file name arrives percent-encoded in markup but not on disk. */
    private static String decodePath(String segment) {
        try {
            return java.net.URLDecoder.decode(segment, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return segment;
        }
    }

    private static boolean isEmpty(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (value.isString()) {
            return value.asString().isBlank();
        }
        return (value.isArray() || value.isObject()) && value.isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Everything the checks read, gathered once. */
    private record Scan(Map<String, ShSite> siteById, List<ShPostUnified> posts,
                        List<ShPostUnified> published, Set<String> allIds,
                        Set<String> publishedIds, List<Scanned> folders) {

        int checked() {
            return posts.size() + folders.size();
        }
    }

    /** A folder with the site that owns it, resolved once via {@link ShFolderSite}. */
    private record Scanned(ShFolder folder, ShSite site) {
    }
}
