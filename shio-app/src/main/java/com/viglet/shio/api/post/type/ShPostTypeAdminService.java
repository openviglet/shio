/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.verify.ShPostReferenceFields;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeRename;
import com.viglet.shio.api.post.type.dto.ShPostTypeRenamed;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.tenant.ShTenantContext;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Read/write surface for post-types as code (SH16) — the engine behind the
 * {@code shio} CLI's {@code pull}/{@code push}. Unlike the read-only, lossy CDA
 * schema (SH11), this exposes (and re-applies) the full authoring model:
 * container widgets ({@code Tab}), verbatim {@code widgetSettings} and the
 * Relator field tree.
 *
 * <p><b>Cascade safety.</b> {@code ShPostTypeAttr} maps {@code shWidget},
 * {@code shPostType} and {@code shParentPostTypeAttr} as {@code @ManyToOne(cascade
 * = ALL)} — so a naive orphan-removal of an attribute on update would cascade a
 * {@code REMOVE} into the <em>shared</em> widget catalog (and even the post-type
 * itself). Before replacing the attribute set we therefore detach those
 * associations ({@link #detach}) so only the attributes are deleted.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
public class ShPostTypeAdminService {

    private final ShPostTypeRepository shPostTypeRepository;
    private final ShWidgetRepository shWidgetRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeAdminMapper mapper;
    private final ObjectMapper objectMapper;

    public ShPostTypeAdminService(ShPostTypeRepository shPostTypeRepository,
                                  ShWidgetRepository shWidgetRepository,
                                  ShPostUnifiedRepository shPostUnifiedRepository,
                                  ShPostTypeAdminMapper mapper,
                                  ObjectMapper objectMapper) {
        this.shPostTypeRepository = shPostTypeRepository;
        this.shWidgetRepository = shWidgetRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ShPostTypeDef> list() {
        return shPostTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(ShPostType::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(mapper::toDef)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ShPostTypeDef> get(String name) {
        return Optional.ofNullable(shPostTypeRepository.findByName(name)).map(mapper::toDef);
    }

    /**
     * Create the post-type named by {@code def} — the non-clobbering half of the
     * surface (SH117). {@code PUT} is an upsert by definition, which is what the CLI
     * {@code push} wants and exactly what the console's "new post-type" form must
     * not do: a name collision there is a mistake to report, not content to
     * overwrite. Conflicts on an existing name; otherwise identical to
     * {@link #upsert}.
     */
    @Transactional
    public ShPostTypeDef create(ShPostTypeDef def) {
        String name = def != null ? def.name() : null;
        if (shPostTypeRepository.findByName(name) != null) {
            throw new ShPostTypeExistsException(name);
        }
        return upsert(name, def);
    }

    /**
     * Create or fully replace the post-type named {@code name} from {@code def}
     * (idempotent by name). Rejects writes to a system post-type, and — when the
     * write does <em>not</em> claim code ownership (the console; see
     * {@link ShPostTypeDef#claimsCodeOwnership()}) — to a code-managed one.
     */
    @Transactional
    public ShPostTypeDef upsert(String name, ShPostTypeDef def) {
        ShPostType existing = shPostTypeRepository.findByName(name);
        if (existing != null && existing.getSystem() == 1) {
            throw new ShSystemPostTypeException(name);
        }
        boolean codeOwned = def.claimsCodeOwnership();
        // SH66/SH117: a hand edit must not silently win a race against the
        // repository that owns the model. The console's editor already disables
        // save on a managed type; this is the server-side backstop.
        if (existing != null && existing.isManaged() && !codeOwned) {
            throw new ShManagedPostTypeException(name);
        }

        ShPostType postType = existing != null ? existing : newPostType(name);
        postType.setTitle(def.title());
        postType.setNamePlural(def.namePlural());
        postType.setDescription(def.description());
        postType.setWorkflowPublishEntity(def.workflowPublishEntity());
        postType.setManaged(codeOwned);
        // SH768 — declared disposable at creation and never afterwards, which is SH591's guarantee
        // for a scratch site and holds for the same reason: a flag a later write could raise would
        // be a way to make a real post-type sweepable in bulk, and the sweep must not become that.
        // So an update leaves the column exactly as the create left it, whatever this def claims.
        if (existing == null) {
            postType.setScratch(def.declaresScratch() ? Boolean.TRUE : null);
        }
        if (existing != null) {
            postType.setModifier(currentUser());
            postType.setModifiedDate(Instant.now());
        }

        if (existing != null) {
            // Detach cascade-bearing associations so the orphan removal below
            // deletes only the old attributes, never the shared widget catalog.
            flatten(postType.getShPostTypeAttrs()).forEach(this::detach);
            postType.getShPostTypeAttrs().clear();
            shPostTypeRepository.saveAndFlush(postType);
        }

        postType.setShPostTypeAttrs(buildAttrs(def.fields(), postType, null));
        return mapper.toDef(shPostTypeRepository.save(postType));
    }

    /**
     * Create a post-type from {@code def} in the <em>current</em> tenant, preserving
     * its {@code system} flag (SH22 Part C — per-tenant provisioning). Unlike
     * {@link #upsert} this is allowed to create system post-types — the point is to
     * seed a freshly-provisioned tenant with a copy of the default tenant's model —
     * but it never overwrites: idempotent, a no-op if a post-type of that name
     * already exists in the current tenant (returns {@code false}). A brand-new
     * {@link ShPostType} is built (fresh id assigned by {@code @VigletAssignableUuidGenerator}),
     * so the row lands under {@code ShTenantContext}'s tenant via Hibernate's
     * {@code @TenantId}; the attribute graph is rebuilt with {@link #buildAttrs}
     * (resolving the shared, non-tenant-scoped widget catalog).
     */
    @Transactional
    public boolean provisionFromDef(ShPostTypeDef def) {
        if (def == null || def.name() == null || shPostTypeRepository.findByName(def.name()) != null) {
            return false;
        }
        ShPostType postType = newPostType(def.name());
        postType.setSystem((byte) (def.system() ? 1 : 0));
        // Provisioning copies the source tenant's flag verbatim (a *copy*, not a
        // write), so absent means "not managed" here rather than SH117's
        // "code-owned write" default.
        postType.setManaged(Boolean.TRUE.equals(def.managed()));
        postType.setTitle(def.title());
        postType.setNamePlural(def.namePlural());
        postType.setDescription(def.description());
        postType.setWorkflowPublishEntity(def.workflowPublishEntity());
        postType.setShPostTypeAttrs(buildAttrs(def.fields(), postType, null));
        shPostTypeRepository.save(postType);
        return true;
    }

    /**
     * Adds the fields {@code def} declares and the existing post-type lacks (SH152).
     *
     * <h2>Why additive, and only additive</h2>
     *
     * <p>A blueprint provisions a model it does not own, so it may not {@code upsert}: that would
     * revert a field a human added and flip SH66's {@code managed} flag from a package the user
     * never wrote. Reporting the difference instead is honest but stops one step short of useful —
     * the caller reads {@code exists · Page · missing: description, sections} and, until they add
     * those by hand, the package's content is writing fields the console cannot render. Since
     * SH168 it is worse than that: those writes are now <em>refused</em>, so the report names a
     * wall rather than a wart.</p>
     *
     * <p>So this adds what is missing and nothing else. It never changes a field's widget or its
     * settings, never removes one, never reorders — and never touches a system type. Every one of
     * those exclusions is a thing somebody else may own.</p>
     *
     * <h2>The two decisions worth stating</h2>
     *
     * <p><b>Ordinal:</b> appended after the highest existing one. Honouring the package's own
     * ordinals would reorder somebody's form, and a form that rearranged itself because a
     * blueprint was applied is a surprise with no upside.</p>
     *
     * <p><b>Top-level only:</b> a nested Relator child that differs stays in {@code missing}.
     * Merging inside a container means deciding whose child order wins, which is a change to an
     * existing field rather than an addition — the thing this method promises not to do.</p>
     *
     * @return the names of the fields it added, in the order it added them; empty when there was
     *         nothing to do, the type is a system type, or it does not exist
     */
    @Transactional
    public List<String> mergeMissingFields(ShPostTypeDef def) {
        List<ShPostTypeAttrDef> missing = mergeableFields(def);
        if (missing.isEmpty()) {
            return List.of();
        }
        ShPostType existing = shPostTypeRepository.findByName(def.name());
        Set<ShPostTypeAttr> attrs = existing.getShPostTypeAttrs();
        int nextOrdinal = 1 + (attrs == null ? 0 : attrs.stream()
                .mapToInt(ShPostTypeAttr::getOrdinal).max().orElse(0));

        List<String> added = new ArrayList<>();
        for (ShPostTypeAttrDef field : missing) {
            ShPostTypeAttrDef appended = new ShPostTypeAttrDef(field.name(), field.label(),
                    field.description(), field.widget(), field.required(), field.isTitle(),
                    field.isSummary(), nextOrdinal++, field.widgetSettings(), field.fields());
            // One at a time, through the same builder every other path uses, so an unknown
            // widget is still the SH80 teaching 400 rather than a half-merged type.
            attrs.addAll(buildAttrs(List.of(appended), existing, null));
            added.add(field.name());
        }
        shPostTypeRepository.save(existing);
        return List.copyOf(added);
    }

    /**
     * What {@link #mergeMissingFields} <em>would</em> add, without adding it (SH153).
     *
     * <p>A blueprint plan has to report a merge before it happens, and it may not write to
     * find out: the plan is the read-only half of the surface. Both callers share this method
     * rather than each deciding what "missing" means, so a plan cannot promise a field the
     * apply then skips.</p>
     *
     * @return the names, in the order a merge would append them
     */
    @Transactional(readOnly = true)
    public List<String> mergeableFieldNames(ShPostTypeDef def) {
        return mergeableFields(def).stream().map(ShPostTypeAttrDef::name).toList();
    }

    private List<ShPostTypeAttrDef> mergeableFields(ShPostTypeDef def) {
        if (def == null || def.name() == null || def.fields() == null) {
            return List.of();
        }
        ShPostType existing = shPostTypeRepository.findByName(def.name());
        if (existing == null || existing.getSystem() == 1) {
            return List.of();
        }
        Set<ShPostTypeAttr> attrs = existing.getShPostTypeAttrs();
        Set<String> present = attrs == null ? Set.of() : attrs.stream()
                .map(ShPostTypeAttr::getName)
                .filter(name -> name != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return def.fields().stream()
                .filter(field -> field.name() != null && !present.contains(field.name()))
                .toList();
    }

    /**
     * Rename a post-type, carrying its content with it (SH163).
     *
     * <h2>Why this is an operation rather than a `PUT` to the new name</h2>
     *
     * <p>This surface is keyed by name, which is right — it is how SH79 addresses everything —
     * and it means the one thing a `PUT` cannot express is a rename. `PUT /post-type/NewName`
     * does not rename {@code OldName}: it <em>creates a second post-type</em>, reports a
     * perfectly successful write, and leaves the original holding its id, its attribute rows and
     * every {@code sh_post_unified} row that points at it. That is SH134's defect for posts, one
     * level up, and it has the same answer — one operation that moves, rather than two that
     * happen to leave the right rows behind.</p>
     *
     * <p>The content does not move at all, and that is the point: a post references its type by
     * <b>id</b>, so renaming the row is the whole job. What has to be found by hand is the one
     * place the model stores a post-type <em>by name</em> — a {@code Multi Select}'s
     * {@code multiselect.postType} (SH154) — because a reference that silently stopped resolving
     * is exactly the kind of breakage a rename is supposed not to cause.</p>
     *
     * <h2>The four refusals</h2>
     *
     * <ul>
     *   <li>an unknown source — 400 with the installed names ({@link ShUnknownPostTypeException});</li>
     *   <li>a blank target — 400 ({@link ShMissingPostTypeNameException}); renaming to nothing is
     *       a delete nobody asked for;</li>
     *   <li>an occupied target — 409 ({@link ShPostTypeExistsException}); merging two models is a
     *       different operation with different consequences, and guessing which was meant would
     *       be the worst possible guess;</li>
     *   <li>a system type — 409 ({@link ShSystemPostTypeException}); and a code-managed type
     *       against a hand edit — 409 ({@link ShManagedPostTypeException}), the same
     *       {@code managed}-claim rule {@link #upsert} follows, because a rename is a bigger
     *       change than the edits that rule already guards.</li>
     * </ul>
     *
     * <p>Renaming to the same name is a <b>no-op</b>, not a conflict: the operation is then
     * idempotent, which is what lets a caller re-run a script without reading the outcome
     * first (P5).</p>
     */
    @Transactional
    public ShPostTypeRenamed rename(String from, ShPostTypeRename request) {
        String to = request == null || request.to() == null ? null : request.to().trim();
        ShPostType existing = shPostTypeRepository.findByName(from);
        if (existing == null) {
            throw new ShUnknownPostTypeException(from, postTypeNames());
        }
        if (to == null || to.isBlank()) {
            throw new ShMissingPostTypeNameException();
        }
        if (existing.getSystem() == 1) {
            throw new ShSystemPostTypeException(from);
        }
        if (existing.isManaged() && !request.claimsCodeOwnership()) {
            throw new ShManagedPostTypeException(from);
        }
        if (to.equals(from)) {
            return renamed(from, to, existing, List.of());
        }
        if (shPostTypeRepository.findByName(to) != null) {
            throw new ShPostTypeExistsException(to);
        }

        existing.setName(to);
        existing.setModifier(currentUser());
        existing.setModifiedDate(Instant.now());
        shPostTypeRepository.save(existing);
        return renamed(from, to, existing, retargetReferences(from, to));
    }

    private ShPostTypeRenamed renamed(String from, String to, ShPostType postType,
                                      List<String> references) {
        return new ShPostTypeRenamed(from, to,
                shPostUnifiedRepository.countByShPostType_Id(postType.getId()), references,
                List.of("A content file's `type:` and a desired-state document's `type` name this "
                        + "post-type too. The server does not write project files: update "
                        + "shio/content/**, shio/post-types/" + from + ".json and any document "
                        + "that declares `" + from + "`, then re-run `shio push --check`."));
    }

    /**
     * Rewrites every {@code multiselect.postType} that named {@code from} (SH154).
     *
     * <p>The one structural reference to a post-type <em>by name</em> in the whole model, which
     * is why it is a list and not a sweep: {@code sh_post_unified} points at the type by id, the
     * CDA schema is served by name and resolves live, and a {@code Content Select} stores a post
     * id rather than a type. Anything else that names a type is prose or a project file, and both
     * are reported rather than rewritten.</p>
     *
     * <p>The setting is stored as a verbatim JSON string and comes back re-serialized, so
     * whitespace inside it is not preserved. That is the cost of editing one key rather than
     * asking the caller to re-push the type, and it is worth paying: the alternative leaves a
     * field silently narrowing to a post-type that no longer exists.</p>
     *
     * @return {@code PostType.field} for each rewrite, in a deterministic order
     */
    private List<String> retargetReferences(String from, String to) {
        List<String> updated = new ArrayList<>();
        for (ShPostType postType : shPostTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(ShPostType::getName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            boolean touched = false;
            for (ShPostTypeAttr attr : flatten(postType.getShPostTypeAttrs()).stream()
                    // SH722 — ordinal alone is not a total order over a hash-ordered Set.
                    .sorted(Comparator.comparing(ShPostTypeAttr::getOrdinal)
                            .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()))
                    .toList()) {
                if (from.equals(ShPostReferenceFields.referencedPostType(attr, objectMapper))
                        && retarget(attr, to)) {
                    updated.add(postType.getName() + "." + attr.getName());
                    touched = true;
                }
            }
            if (touched) {
                shPostTypeRepository.save(postType);
            }
        }
        return List.copyOf(updated);
    }

    private boolean retarget(ShPostTypeAttr attr, String to) {
        try {
            JsonNode settings = objectMapper.readTree(attr.getWidgetSettings());
            if (!(settings instanceof ObjectNode root)
                    || !(root.get("multiselect") instanceof ObjectNode multiselect)) {
                return false;
            }
            multiselect.put("postType", to);
            attr.setWidgetSettings(objectMapper.writeValueAsString(root));
            return true;
        } catch (JacksonException unreadable) {
            // A settings string that does not parse cannot have been read as a reference in the
            // first place, so there is nothing here to retarget — and failing the rename over
            // somebody else's malformed field would be the wrong trade.
            return false;
        }
    }

    /** The request's tenant, for the native counts that bypass {@code @TenantId} (SH22). */
    private static String tenantId() {
        String tenant = ShTenantContext.getTenantId();
        return tenant != null ? tenant : ShTenantContext.DEFAULT_TENANT;
    }

    /** The installed post-type names, sorted — the {@code allowed} set of a teaching 400. */
    private List<String> postTypeNames() {
        return shPostTypeRepository.findAll().stream()
                .map(ShPostType::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .toList();
    }

    /**
     * Delete a non-system post-type that no content points at. Returns {@code false} if it does
     * not exist.
     *
     * <p><b>A type still in use is a teaching 409, not a 500 (SH236).</b> This used to check the
     * {@code system} flag and nothing else, and the post FK is {@code onDelete NO ACTION} — so
     * deleting a type with content raised a constraint violation that reached the caller as a
     * server fault. The count in the refusal is what makes it actionable: it distinguishes a
     * rename (SH163, which keeps the content) from a genuine retirement.</p>
     *
     * <p>The count includes the trash, because a trashed post still holds its
     * {@code post_type_id} and would still block the delete — a refusal saying "0 posts" followed
     * by a 500 is the worst of both.</p>
     */
    @Transactional
    public boolean delete(String name) {
        ShPostType existing = shPostTypeRepository.findByName(name);
        if (existing == null) {
            return false;
        }
        if (existing.getSystem() == 1) {
            throw new ShSystemPostTypeException(name);
        }
        long live = shPostUnifiedRepository.countByShPostType_Id(existing.getId());
        long total = shPostUnifiedRepository.countAllByPostTypeIncludingTrash(existing.getId(),
                tenantId());
        if (total > 0) {
            throw new ShPostTypeInUseException(name, live, Math.max(total - live, 0));
        }
        flatten(existing.getShPostTypeAttrs()).forEach(this::detach);
        existing.getShPostTypeAttrs().clear();
        shPostTypeRepository.delete(existing);
        return true;
    }

    private ShPostType newPostType(String name) {
        ShPostType postType = new ShPostType();
        postType.setName(name);
        postType.setSystem((byte) 0);
        postType.setDate(Instant.now());
        postType.setOwner(currentUser());
        return postType;
    }

    private Set<ShPostTypeAttr> buildAttrs(List<ShPostTypeAttrDef> defs, ShPostType postType,
                                           ShPostTypeAttr parent) {
        Set<ShPostTypeAttr> attrs = new LinkedHashSet<>();
        if (defs == null) {
            return attrs;
        }
        for (ShPostTypeAttrDef def : defs) {
            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName(def.name());
            attr.setLabel(def.label());
            attr.setDescription(def.description());
            attr.setOrdinal(def.ordinal());
            attr.setRequired((byte) (def.required() ? 1 : 0));
            attr.setIsTitle((byte) (def.isTitle() ? 1 : 0));
            attr.setIsSummary((byte) (def.isSummary() ? 1 : 0));
            attr.setWidgetSettings(def.widgetSettings());
            if (def.widget() != null) {
                ShWidget widget = shWidgetRepository.findFirstByName(def.widget());
                if (widget == null) {
                    // Only on the error path: load the catalog so the 400 can teach
                    // the allowed names + nearest match instead of just rejecting (SH80).
                    throw new ShUnknownWidgetException(def.widget(), widgetCatalogNames());
                }
                attr.setShWidget(widget);
            }
            attr.setShPostType(postType);
            attr.setShParentPostTypeAttr(parent);
            attr.setShPostTypeAttrs(buildAttrs(def.fields(), postType, attr));
            attrs.add(attr);
        }
        return attrs;
    }

    /** Depth-first flatten of an attribute tree (top-level + every nested child). */
    private List<ShPostTypeAttr> flatten(Set<ShPostTypeAttr> attrs) {
        if (attrs == null) {
            return List.of();
        }
        return attrs.stream()
                .flatMap(attr -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(attr),
                        flatten(attr.getShPostTypeAttrs()).stream()))
                .toList();
    }

    private void detach(ShPostTypeAttr attr) {
        attr.setShWidget(null);
        attr.setShParentPostTypeAttr(null);
        attr.setShPostType(null);
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "admin";
    }

    /**
     * The installed widget names, sorted — the {@code allowed} set of an
     * unknown-widget teaching error (SH80). Sorted so an identical failure always
     * produces an identical body (P5).
     */
    List<String> widgetCatalogNames() {
        return shWidgetRepository.findAll().stream()
                .map(ShWidget::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
