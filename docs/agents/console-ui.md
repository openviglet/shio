# Console UI — React patterns, widgets, data model

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## Architecture Patterns (follows Turing React)

### Service Layer
Class-based services with axios. Each domain has its own service:
```typescript
export class ShSiteService {
  async query(): Promise<ShSite[]> { ... }
  async get(id: string): Promise<ShSite> { ... }
  async create(site: ShSite): Promise<ShSite> { ... }
  async update(site: ShSite): Promise<ShSite> { ... }
  async delete(id: string): Promise<void> { ... }
}
```

### Context Providers
- `ThemeProvider` - Dark/light mode
- `BreadcrumbProvider` - Navigation breadcrumb state
- `UserProvider` - Current user from `/api/v2/user/current`
- `ShioServiceContext` - Centralized service instances

### API Base
- Backend: `http://localhost:2710`
- React dev: `http://localhost:5173` (Vite proxy to backend)
- API prefix: `/api/v2/`
- Post XP endpoint: `/api/v2/post/xp/{id}` (returns post with attrs for editing)

## UI Design System

### Available shadcn/ui Components
Located in `src/components/ui/`:
avatar, badge, breadcrumb, button, card, checkbox, dialog, dropdown-menu, form,
gradient-button, input, label, select, separator, sheet, sidebar, skeleton, sonner,
switch, table, tabs, textarea, tooltip

**Not available** (do not attempt to import): accordion, collapsible, popover, command, calendar

### Widget Color System (`src/lib/widget-colors.ts`)
Shared color constants used across both the post-type editor and post form rendering.
Each widget type has a unique color for visual identification:

| Widget | Badge Color | Accent |
|---|---|---|
| Text | Blue | `border-l-blue-500` |
| Text Area | Indigo | `border-l-indigo-500` |
| HTML Editor | Purple | `border-l-purple-500` |
| Check Box | Green | `border-l-green-500` |
| Combo Box | Teal | `border-l-teal-500` |
| Date | Amber | `border-l-amber-500` |
| Hidden | Gray | `border-l-gray-400` |
| Multi Select | Cyan | `border-l-cyan-500` |
| Ace Editor - JS | Yellow | `border-l-yellow-500` |
| Ace Editor - HTML | Orange | `border-l-orange-500` |
| Ace Editor - CSS | Sky | `border-l-sky-500` |
| Content Select | Rose | `border-l-rose-500` |
| File | Pink | `border-l-pink-500` |
| Relator | Violet | `border-l-violet-500` |
| Tab | Slate | `border-l-slate-500` |

Helper functions: `getWidgetBadgeColor(name)`, `getWidgetAccentColor(name)` return appropriate classes with fallback defaults.

**Usage**: Import from `@/lib/widget-colors` in any component that needs widget-type-aware styling.

### Page Layout Pattern
Editor pages follow a consistent layout:
1. `<PageHeader>` with icon and breadcrumb
2. **Sticky save bar** - `sticky top-0 z-10` with `bg-orange-50 dark:bg-orange-950/30` orange accent:
   - Left: title + metadata badges (post type, field count, publish permission)
   - Right: Save, Save & Close, Cancel buttons
3. **Card(s)** containing the form content

### Field Card Pattern
Form fields (both in post-type editor and post form) render as individual cards:
- `border-l-4` with widget-type accent color
- `rounded-lg border bg-background`
- `hover:shadow-sm transition-shadow` for interactivity
- Header row with label, required indicator (`*`), and inline badges (Title, Summary, widget type)
- Widget component renders inside the card
- Optional description text below

### Empty State Pattern
When a list has no items, show a centered empty state:
```tsx
<div className="flex flex-col items-center justify-center py-16 text-center">
  <div className="rounded-full bg-muted p-4 mb-4">
    <Icon className="h-8 w-8 text-muted-foreground" />
  </div>
  <h3 className="font-semibold text-lg mb-1">Title</h3>
  <p className="text-sm text-muted-foreground max-w-sm mb-6">Description</p>
  <Button>CTA</Button>
</div>
```

### Toggle Preferences
- Use `Switch` for boolean toggles in settings/editor forms (modern look)
- Use `Checkbox` only inside form widgets (e.g., CheckBoxWidget for content editing)
- Use `Tooltip` with `Info` icon for field help text instead of permanent description paragraphs

### Badge Patterns
- **Widget type badge**: Colored per widget using `WIDGET_BADGE_COLORS`, variant `outline`
- **Required badge**: `border-amber-300 text-amber-600` (dark: `border-amber-700 text-amber-400`)
- **Title badge**: "T" with `border-blue-300 text-blue-600`
- **Summary badge**: "S" with `border-green-300 text-green-600`
- **Publish badge**: Shield icon with green background
- **Field count badge**: `variant="secondary"` with count number

## Widget System

### Dynamic Form Rendering
Widgets are React components that render based on the field's `widget` name
(`ShPostTypeSchemaField.widget`, projected from the post-type's `fields` by
**`postFormSchema()`** — the one exported entry point, SH178. It used to read
`shPostTypeAttr.shWidget.name` (SH117), and there used to be a second export with no fallback,
which is why creating a post worked and editing it did not.)
Registry in `src/components/widgets/index.ts` maps widget names to components via `WIDGET_MAP`.
`getWidget(name)` returns the component, defaulting to `TextWidget` for unknown names.

**A widget is a name, and after SH285 the row says so.** `ShWidget` carried six columns; three named
artefacts this repository does not contain — `class_name` (`com.viglet.shio.widget.Sh*Widget`, a
package that today holds only the `ShSystemWidget` constants) and `implementation_code` /
`setting_path` (`template/widget/**`, Thymeleaf paths that left with the AngularJS console). Nothing
read them and **two were writable through `PUT /api/v2/widget/{id}`**, so a caller could set a
template path, get a 200, and conclude widgets are extensible by pointing them at a file. They are
dropped (`v2026.3.16.yaml`), out of `ShWidgetRequest`, out of the seed, and out of the console's
`ShWidget` model. What remains is `id`, `name`, `description`, `type` — and `ShWidgetShapeLintTest`
pins that set, so the next column has to name what reads it. `description` and `type` stayed on
SH293's line: they state something **true** and merely unread, where the three that went named
something **absent**. Extending the widget catalogue is still a React change plus a seed row — the
name is the contract, and there is no server-side extension point to look for.

### Widget Names (from database)
| Widget Name | Component | Data Field |
|---|---|---|
| `Text` | Input | `strValue` |
| `Text Area` | Textarea (6 rows) | `strValue` |
| `HTML Editor` | contentEditable with formatting toolbar (Bold, Italic, Underline, Lists, Link, Code) | `strValue` |
| `Check Box` | Checkbox (multiple, parsed from widgetSettings choices) | `arrayValue` |
| `Combo Box` | Select dropdown (parsed from widgetSettings choices, optional null) | `strValue` |
| `Date` | Input type=date with clear button | `dateValue` (ISO string) |
| `Hidden` | hidden input (not rendered visually) | `strValue` |
| `Multi Select` | Free-form tag input with Enter to add, X to remove | `arrayValue` |
| `Ace Editor - Javascript` | CodeMirror (javascript mode, line numbers, folding) | `strValue` |
| `Ace Editor - HTML` | CodeMirror (html mode) | `strValue` |
| `Ace Editor - CSS` | CodeMirror (SH252 — a `Theme`'s stylesheet) | `strValue` |
| `Content Select` | ID input + status indicator + clear button | `strValue` |
| `File` | ID input + status indicator + clear button | `strValue` |
| `Relator` | Nested items list with add/remove (uses `crypto.randomUUID()`) | `shChildrenRelatorItems` |
| `Tab` | Groups fields into tabs (container, no data) | none |

### WidgetProps Interface
```typescript
interface WidgetProps {
  attr: ShPostAttr       // Current field value and metadata
  onChange: (attr: ShPostAttr) => void  // Callback for changes
  postId?: string        // ID of post being edited
}
```

### PostFormFields Component (`src/components/widgets/post-form-fields.tsx`)
Renders the dynamic form for post editing/creation:
1. Sorts attrs by `shPostTypeAttr.ordinal`
2. Groups fields into `TabGroup[]` when Tab widgets are present
3. First group (before any Tab) gets the post type name as label
4. Each tab trigger shows a field count badge
5. Each visible field renders as a **color-coded card** with widget accent border
6. Hidden widgets render invisibly (no card wrapper)
7. Fields show: label with required asterisk, Title/Summary/WidgetType badges, widget component, description

### Tab Widget Behavior
Tab widgets split the form into tabbed sections. The `PostFormFields` component:
1. Sorts attrs by `ordinal`
2. Groups attrs between Tab widgets into `TabGroup[]`
3. First group (before any Tab) gets the post type name as label
4. Renders shadcn `Tabs` with `TabsList` + `TabsContent`

### Widget Settings
Stored as JSON string in `shPostTypeAttr.widgetSettings`. Parsed by each widget:
- CheckBox/ComboBox: `{ "choices": "id:label\nid2:label2", "allowNull": 1 }`
- File: `{ "file": { "folderId": "..." } }`
- ContentSelect: `{ "contentSelect": { "folderId": "..." } }`
- MultiSelect: `{ "multiselect": { "postType": "...", "folderId": "..." } }`
- Relator: `{ "relator": { "title": "..." } }` (plus nested `shPostTypeAttrs`)
- Search/Semantic: `{ "search": { "association": "...", "isAdditionalField": 1, ... } }`

**IMPORTANT**: Always use safe parsing for `widgetSettings`. `JSON.parse("null")` returns `null`, not `{}`. Use the `parseSettings()` helper or guard:
```typescript
const p = JSON.parse(raw);
return p && typeof p === "object" ? p : {};
```

### A field value is not always a reference (SH660)

`file.widget.tsx` renders **two different things** with one component, and that is the whole
finding. On another post's field the value is a *reference* — the content browser writes
`selection.id` — and expanding it into a readable path is the widget's job. On the `File`
post-type's **own `FILE` field** it is the *stored file name*, because `ShStaticFileService` puts
`fileName` there and keeps the bytes under the post's id.

The resolver assumed the first, so opening any uploaded file issued
`GET /v2/post/xp/<name>` and `GET /v2/folder/<name>` — both 404. Nothing reported it: each call ends
`.catch(() => null)`, the widget rendered its null branch, and the page returned **200**. The only
witness was the network tab, which is why it took `console-check.mjs` — a probe that reads *failed
requests* — to see it at all, on the first instance it met with an uploaded file.

**Ask `isObjectReference` before resolving** (`src/lib/object-reference.ts`). The rule is the value's
**shape**, not the field's identity: a reference in this console is an object id and an object id is
a UUID, so anything else is a value the field holds and looking it up is a request that cannot
succeed. The narrow alternative — *"if this is the `File` type's `FILE` field, skip"* — couples a
generic widget to one system post-type and would be wrong for the next field that stores a name.
`content-select.widget.tsx` carries the guard too, as a precaution rather than a fix: its values are
always ids today, and the failure mode is identical and equally silent.

### The Ace Editor widgets need CodeMirror to be a singleton

`ace-editor.widget.tsx` (the HTML / Javascript / CSS widgets) pairs `@uiw/react-codemirror`
with extensions built elsewhere — `html()`, `javascript()`, `githubDark`. CodeMirror 6
matches `Facet`, `StateField` and extension values by **class identity**, so a second copy
of `@codemirror/state` makes `EditorState.create` reject an extension the types say is
valid:

```
Unrecognized extension value in extension set ([object Object]).
```

The editor then renders nothing on a post edit page. Nothing declares the conflict —
`@uiw/react-codemirror` resolved state 6.7.1 / view 6.43.6 while `@codemirror/lang-html`
and `lang-javascript` pulled state 6.6.0 / view 6.43.0 through `language@6.12.3`. Three
copies of state, two of view, every semver range satisfied, `pnpm install` clean.

Two things made it read as somebody else's bug for longer than it should have. The stack
named **`vendor-graphiql-*.js`**, because the manual-chunk rule in
[vite.config.ts](../../shio-react/vite.config.ts) claims every id containing `codemirror`
— so a widget fault surfaced under GraphiQL's name. And the same rule means any post with
an Ace widget eagerly loads that ~4.8 MB chunk, since `WIDGET_MAP` imports the widget
statically; worth splitting if the page's weight is ever the question.

`overrides` in [pnpm-workspace.yaml](../../pnpm-workspace.yaml) pins one version of state,
view and language — **in the workspace file, not `package.json`**, which pnpm 11 no longer
reads for that field. `test/codemirror-singleton.test.mjs` guards it by building the
widget's own extensions and feeding them to the editor's own `EditorState`, so it fails on
any future re-split rather than on a version number; importing `@codemirror/state`
directly would resolve the *test's* copy and pass while the console stayed broken.

## Post Type Editor (`post-type.edit.page.tsx`)

### Layout
- Sticky save bar with title and Save/Save & Close/Cancel buttons
- Card with Tabs: Details (title, description, API name, plural name) | Workflow
- Fields card with drag-and-drop field list

### Field Editor (AttrFieldList + SortableAttrRow)
- Uses `@dnd-kit/core` + `@dnd-kit/sortable` for drag-and-drop reordering
- Each field is a collapsible card with:
  - Left accent border (widget color), numbered circle badge, expand chevron
  - Label (bold), identifier (mono), widget type badge, flag badges (Required, T, S)
  - Icon-only delete button with tooltip
- Expanded state has inner Tabs: Details | Semantic Navigation
  - Details: 2-column layout (label + identifier), field type select, horizontal Switch toggles (Required, Title, Description), widget-specific settings in bordered container
  - Semantic Navigation: search association dropdown, additional search field toggle
- Empty state shown when no fields exist

### Widget-Specific Settings in Post Type Editor
Rendered inside `WidgetSpecificSettings` based on widget name:
- **Check Box / Combo Box**: Choices textarea (monospace, `value: Label` format) + Allow Null switch
- **File**: Folder ID input
- **Content Select**: Folder ID input (key: `contentSelect`)
- **Multi Select**: Post Type dropdown + Folder ID input
- **Relator**: Relator title + recursive `AttrFieldList` for sub-fields

## Content Navigation (GitHub-like file browser)

### Site List Page (`/console/content`)
- Shows all sites as a list with hover actions (Download, Edit, View Site, Delete)
- Tabs: Sites | Tasks
- Search filter
- Click site → navigates to `/console/content/object/{siteId}`

### Object Children Page (`/console/content/object/{objectId}`)
Main navigation page. Shows folders and posts inside any object.
- **Breadcrumb bar**: clickable path (Viglet / Home / Developers)
- **Action bar**: New [PostType] + grid selector, New Folder, Import
- **Sort/Select/Batch** actions
- **Content list**: Each item as a card with position badge, checkbox, icon, name, date, type badges, hover actions
- **New Post**: "New [LastPostType]" button remembers last used (localStorage `sh-last-post-type`), grid icon opens post type selector page

### Post Type Selector (`/console/content/folder/{folderId}/post-type-select`)
Full page with grid of post types:
- System types in compact grid (6 columns)
- Custom types with description (4 columns)
- Alternative actions: "Or create a new Folder", "Or model a new Post Type"

### Post Edit Page (`post.edit.page.tsx`)
- Sticky save bar with post title, post type badge, **state** badge (`DRAFT`/`PUBLISHED` —
  which row the form is editing, *not* a permission), field count badge
- Preview Draft, Translations, History, Schedule, Save, **Publish**, **Unpublish**,
  Save & Close, Cancel buttons
- Card with `PostFormFields` rendering color-coded field cards
- Loading state with animated spinner
- Navigation: back to parent folder on save/cancel

#### Publishing from the edit screen (SH654)

Until SH654 the console had exactly **one** publish call site — the Universal Editor — so a
curator editing a page had to leave for the visual editor or wait for an agent run to be
approved. Nothing on the server was missing: `ShPostUnifiedAPI` already answered
`POST /{id}/publish` and `POST /{id}/unpublish`, under the SH107 gate. Three decisions:

- **Publish sits beside Save**, both primary; `Save & Close` is untouched. Publishing is a
  *distinct* act, not a variant of saving, and P7 (draft-default) means Save keeps being the
  action a curator reaches for by reflex.
- **Publish saves first, then promotes.** The server publishes the **stored** `DRAFT` row, so
  a bare publish would put a version on the site that the curator never saw. `handlePublish`
  is `update()` then `publish()`, in that order, and `publish-action.test.mjs` asserts the
  order — reversing it is silent, and the symptom is somebody else's live page.
- **Unpublish is gated on `isLive`, never on `state`.** A post that was published and then
  edited has **both** rows, so its `state` is `DRAFT` while the site is serving it. `state`
  says which row the form edits; `isLive` says whether a `PUBLISHED` row exists. The load
  therefore fetches both rows with `Promise.allSettled` — one round trip — instead of the old
  sequential try/catch, which could not report the published row once it had found a draft.

`Publish` is hidden when no `DRAFT` row exists (`POST /{id}/publish` answers
`Draft not found`); saving such a post writes the live row directly, so nothing is out of
reach. The **live** badge appears when `isLive && state === "DRAFT"`, because otherwise
Unpublish sits next to a badge reading `DRAFT` and its own precondition is invisible.

### Post New Page (`post.new.page.tsx`)
- Same sticky save bar pattern as edit page
- Builds empty `ShPostAttr[]` from `ShPostTypeAttr[]` on the selected post type
- Derives title/summary from attrs with `isTitle`/`isSummary` flags on submit
- Navigates back to folder after creation

### Delete Dialog (follows Turing pattern)
`DialogDelete` component requires typing the item name to confirm deletion.

## Data Model Notes

### ShPost (from XP endpoint)
```typescript
{
  id, title, summary, furl,
  shPostType: { id, name, title },
  shFolder: { id, name, shSite: { id, name } },
  owner, date, published, publishStatus,
  shPostAttrs: ShPostAttr[]
}
```

### ShPostAttr
```typescript
{
  id, strValue, arrayValue, dateValue, intValue,
  shPostTypeAttr: {
    id, name, label, description, ordinal, required,
    isSummary, isTitle,
    shWidget: { name },
    widgetSettings?: string
  },
  shChildrenPostAttrs?: ShPostAttr[],
  shChildrenRelatorItems?: ShRelatorItem[]
}
```

### ShRelatorItem
```typescript
{
  id: string,
  title?: string,
  summary?: string,
  ordinal: number,
  shChildrenPostAttrs: ShPostAttr[]
}
```

### ShPostType (SH117 — the def shape, no ids, real booleans)
The console model mirrors the backend `ShPostTypeDef`. There is no `id`: a post-type is
addressed by `name`, which is why the editor routes on the name and `POST
/api/v2/post-unified` takes `postType`. The pre-SH117 entity shape (`id`,
`shPostTypeAttrs`, `shWidget: {id, name}`, `0`/`1` flags) is gone along with the endpoint
that served it.
```typescript
{
  name, title?, namePlural?, description?,
  workflowPublishEntity?: string,
  system?: boolean,
  managed?: boolean,   // delivered on read; on write it is a claim — see the API section
  fields?: ShPostTypeField[]
}
```

### ShPostTypeField
```typescript
{
  name, label?, description?,
  widget?: string,          // the widget NAME, e.g. "HTML Editor" — the catalogue is closed
  required?: boolean, isTitle?: boolean, isSummary?: boolean,
  ordinal: number,
  widgetSettings?: string,  // verbatim JSON, kept as a string so a round trip is lossless
  fields?: ShPostTypeField[]  // nested for Relator / Tab
}
```
A field has **no id**, so the post-type editor mints an editor-local `key` per row for
React/dnd identity and strips it before saving — `name` is what the user is typing and
cannot double as an identity.

## Color Theme
- Primary: Orange/Amber (vs Turing's Blue/Indigo)
- Gradients: `from-orange-500 to-amber-600`
- Badges: orange for folders, blue for posts, green for sites
- Sticky save bar: `bg-orange-50 dark:bg-orange-950/30`
- Widget types: each has a unique color (see Widget Color System above)

## i18n
- Languages: English (en), Portuguese (pt)
- Namespace files in `src/i18n/locales/{lang}/`
- Categories: common, content, admin, config, dialog, login, nav, sidebar
- Key naming: `namespace.camelCaseKey` (e.g., `content.fieldLabel`, `content.noFields`)
- Interpolation: `{{variable}}` syntax (e.g., `content.modelPostType: "Model your {{title}} Post Type"`)
- Always add keys to both `en` and `pt` locale files simultaneously — **guarded since SH333** by
  `shio-react/test/i18n-parity.test.mjs`, which asserts both locales carry the same namespaces and
  the same keys, in both directions. It was a convention with no instrument, and the failure it
  prevents is quiet: i18next falls back to the key itself, so a missing `pt` entry renders
  `universalEditor.bridgeAbsent` to a Portuguese curator and is invisible to whoever added it.
- **A string that is not a key at all is the case parity cannot see (SH823).** `i18n-parity` asks
  whether the locales agree; it has nothing to say about a component that never calls `t()`, and
  that is the failure SH50's hand sweep fixed and left unguarded.
  `shio-react/test/console-english.test.mjs` reads every `.tsx` for the two shapes a curator
  actually reads — a **JSX text node** (`>Save changes<`) and a user-visible **string prop**
  (`title=`, `placeholder=`, `label=`, `description=`, `aria-label=`) — and holds the findings at
  exactly the `UNTRANSLATED` list, which is three entries with a reason each: the product name on
  the login and setup screens, and the `FREE` plan placeholder, whose value the API compares (a
  translated placeholder would teach a Portuguese operator to type a word the server rejects).
  Anything interpolated is disqualified by the `{` in the span, which is why the hundreds of
  correct `t(...)` call sites cost no whitelist. It found twelve on its first run — the HTML
  editor's whole toolbar among them — all now routed through `content.editor*`.

## Universal Editor — it saves on the session, not on a token (SH335)

The editor used to save through the **CDA** behind a `WRITE`-scope API token the curator had to mint
in Admin and pick from a dropdown. That is **P10 read backwards** — a console feature reaching its
own content through a third contract — and it cost three things: unusable on a fresh instance (no
token exists yet, and "saves nothing" is how that presents), the most dangerous credential in the
product sitting in `localStorage` to do what the session cookie already authorized, and
`PATCH {publish:true}` carrying publish authority **SH107's gate never saw** (that gate is enforced
in `ShAgentBatchService` on the premise that "a console caller has no token and is unaffected").

- **`PATCH /api/v2/post-unified/{id}`** `{attrs, publish}` is the console's merge verb. `PUT` is
  whole state (SH168 — an upsert declares everything) and the editor edits two or three fields.
- **The decision that had to come first** was a client read-modify-write versus a merge verb. The
  verb, because the first reintroduces a lost update on a product whose premise is several curators
  on one site — `theMergeLeavesAFieldItWasNotGiven` is that property.
- **`ShPostDraftMergeService` is the merge, once, with two guards over it**: the CDA checks its SH2
  token allow-list, the console checks the SH176 content ACL. Authorization is what differs between
  the surfaces; the merge never was.
- The token picker, `UE_TOKEN_KEY` and `labelSource`'s `hasToken` argument are **gone**. Labels used
  to resolve from the post only for whoever had minted a token and silently from the page's type for
  everyone else — degrading by credential rather than by what the selection said.

## Universal Editor — the iframe has three states, not two (SH333)

`visual-editor.page.tsx` frames the page and waits for SH185's `sh:ready`. It had no `onLoad`, no
`onError` and no deadline, so the badge read `Connecting…` **forever** when the handshake could
never complete — a curator opening the editor on a site with no page at `/` got that badge over a
canvas showing the renderer's teaching 404, a body written for an API caller (its `fix` is a `GET`
the reader cannot run). "Still loading" and "this will never load" were the same word.

- **`BridgeState` is one field** (`waiting` / `ready` / `absent`), not a `ready` boolean beside a
  second flag: two fields that must agree about one handshake can disagree about it.
- **A timeout, never inspecting the framed document.** Same reason SH185 pins the origin instead of
  posting to `"*"`: the editor must work against an external front end whose DOM this window cannot
  read, so anything learned by inspection works for Shio's renderer and silently fails the case the
  protocol exists for. The clock starts on `onLoad`, not when `src` is set — starting early is how a
  slow site gets told it is broken.
- **`absent` is amber and is not an error.** No bridge has two causes that look identical from here:
  an error page, or a front end that ignores `?editor=true` — which SH185 calls the wanted
  behaviour, degrading to a plain iframe. The banner keeps the frame visible and says editing is
  unavailable; it never claims the site is broken.

## Universal Editor — it is driven now, and two conditions had to be discovered (SH659)

SH238 closed the one fact the save-back rests on and recorded the drive as blocked on "a front end
to embed — which is Block P". **The blocker was already stale**: SH183/SH184 make a site with no
configured `url` fall back to Shio's own renderer, and `?editor=true` asks it for the SH185 bridge,
so the thing to embed ships with the product. `console-check.mjs`' `universal-editor-drive` now
asserts the three messages nothing observed — `sh:ready` (the bridge turned edit mode on),
`sh:element-selected` (a click fills the properties panel) and `sh:content-changed` (typing becomes
a pending change). Until it existed, a bridge that never loaded and one that works were the same
observation to every gate.

**Same origin, or there is no handshake — and this is the one to know when the editor "does not
work" on your machine.** The bridge posts to `data-shio-editor-origin`, which `ShRenderAPI.originOf`
derives from the request; an iframe navigation carries no `Origin` header, so it falls back to the
*server's* own origin. Production serves the console and the renderer from one origin and this is
exactly right. A Vite console on `:5173` framing a renderer proxied from `:2710` gets a bridge
aimed at a window that is not its parent, so the panel stays empty and the badge reads *"Sem
conexão"* over a page that is fine. **Run `console:check --base http://localhost:2710`** — the
instance's own console — or the probe skips, naming the topology rather than failing.

**And the site is chosen by asking the renderer.** An empty `url` is necessary and not sufficient: a
site whose home is absent frames the teaching 404, which answers **200** and carries
`data-shio-prop` of its own, so neither the status nor the annotations alone can tell it from a real
page. The probe requires both the props and the bridge tag, and skips when no site on the instance
has them — a fixture gap is not a defect.

The round trip stops at the pending change: `universal-editor-save-back` already proves the PATCH it
becomes, and a smoke check must not edit anybody's content.

**Publish is driven too, and only on a site this repository made (SH659).** The reason it was left
out was right and still is — the fix is not to relax it but to give it a boundary. SH664 made the
site `provision.mjs` creates the drive's own target, so there is now a page we own end to end, and
publishing *that* is not editing anybody's content. The guard reads the **description**
`provision.mjs` writes, never the `--site` the caller passed: `console:check --site Demo` must not
publish somebody's home page because they named it. Candidates are also **ordered** so ours is tried
first — before this the winner was whichever site the API listed earliest, which on a developer's
instance is a real one. Ordering and not filtering: an instance with no fixture still gets the
drive, it just does not get publish.

Why it is worth a step of its own: `persist(true)` is a different path from the one already covered
— it PATCHes with `publish: true`, and publishes the selected post outright when nothing was edited.
It is asserted on the **toast**, not on the change count, because `persist` clears the pending map
on success and on failure alike. Under `--strict` a skipped publish is a failure for SH667's reason,
and `test/e2e-fixture-site.test.mjs` holds the marker against the file that writes it — a marker
that drifts turns publishing off silently, which reads exactly like a run that published.

**The fixture had to stop declaring a front end before any of that could run on CI's own site
(SH664).** `provision.mjs` created the console-check site with `url: API` — the instance's own
origin — from the commit that introduced the file, written before SH183/SH184 existed. A non-empty
`url` means *external front end*, so two things followed and neither failed anything: `siteDestination`
aimed the console's open-site link and the editor's iframe at the instance, where `GET /` answers
**302 to `/login`** — the console rendering its own login page inside its own iframe — and the drive,
which excludes every url-bearing site *before* it asks the renderer anything, never had the one site
CI provisions on purpose as a candidate. Empty is what a site an agent just built looks like, which
is the case SH183/SH184 exist for.

Measured against a live instance rather than argued: with `url` dropped the site stores `url: null`
and its `/preview/<name>/?editor=true` carries **14** `data-shio-prop` elements and the bridge
script — every conjunct of the probe's own predicate. `test/e2e-fixture-site.test.mjs` holds the
pair together, reading the exclusion out of `console-check.mjs` and the declaration out of
`provision.mjs`: neither half is wrong alone, and a test that read only one would be asserting a
preference instead of a coupling.

**And in CI the drive is not allowed to skip — `--strict` (SH238/SH659).** A skipped probe keeps
`ok: true`, counts toward the `N/N probes ok` line and leaves the exit code at 0, which is why
SH664 survived from the commit that introduced `provision.mjs` and was found by reading the code
rather than by a red run. On a laptop the skip is right: the topology and the fixtures are whatever
they happen to be, and a gate that trips on that is a gate people bypass. In CI both are
provisioned by this repository one step earlier, so a skip there is this instrument not running.
`console-check.yml` passes `--strict`, and under it **any** drive skip is a failure — applied once
where the probe is reported rather than at each `probe.skipped =`, so a skip path added later
cannot be the one that forgot. The `skipped` reason still reaches the report; `--strict` only
decides whether it is allowed.

An explicit flag rather than `process.env.CI`, because `cli/test/ci-gates.test.mjs` can then assert
**both** halves — the workflow sends it *and* the script acts on it. That is SH655's own shape one
instrument over: a signal nothing asserts is the same absent gate with an extra step, and a flag
passed to a script that ignores it is the same again.

## The review count that could not go down (SH615)

`ShReviewSession.pending` asked, per audit entry, whether the post has a `DRAFT` row. Its own javadoc
said what it meant to answer — *"a change whose post is gone, or that is already published, is
history rather than a decision"* — and it could not, because **`publish` writes a `PUBLISHED` row and
keeps the draft** as the editable copy. Every post that has ever existed has one, forever.

So the number only grew. A curator who approved a run saw the page reload with the same *"3 to
review"*, and Approve stayed enabled on work already published. No error and nothing logged: the page
asserting something untrue about the one thing it exists to resolve.

**The console needed no change.** `review.list.page.tsx` already gates both the badge and the Approve
button on `session.pending` — the stuck number is precisely why the gate never closed. Fixing the
source made both settle, which is P1 working in the ordinary direction.

### Two defects, fixed together on purpose

SH207 had already written the correct definition one method away, because a navigation badge has to
be **safely ignorable at zero** and so could not reuse this one. `unreleased(...)`: a draft with no
published twin, or one modified *after* its twin was published. Approving is exactly what makes both
false.

The second defect is the one that decided the shape. `pending` counted **entries**, not posts, so one
page edited four times reported four. Fixing only the staleness would have left a number that is
fresh and still not a count of decisions — **and that reads as correct**, which is worse than one
that is visibly stuck. Both or neither.

The set is now resolved **once per page** in `sessions(...)` rather than per session, which is the
two-query shape `pending()` already used. The old code ran `state(id, DRAFT)` inside the per-entry
loop: one query per audit row, answering a question that was wrong anyway.

### What the pin was worth

SH207 did not fix this; it **pinned** it, with an assertion asserting `3` and a message saying
"pinned rather than fixed: making the two agree is a decision about which question the page is
asking". That is the right way to leave a disagreement — stated, not hidden — and it is why SH615 was
a small change rather than a rediscovery. `ShReviewPendingTest` now asserts the two counts agree, and
that a run's own count **reaches zero** once its work is released, which is the assertion that would
have caught the original (SH527).

## A single-tenant install does not advertise the tenant admin (SH46)

`shio.multi-tenant` is **off by default**, so almost every install is single-tenant. The page
header had gated the tenant switcher on `discovery.multiTenant` since SH22; the sidebar's Tenants
item was never gated at all. So the default install offered a curator a page for managing tenants
it does not have, and `GET /api/v2/tenant/current` answers `shio` there whatever they do on it —
a surface that accepts attention and returns nothing, which is what P7 is about.

The item is now spread into the group behind the same flag the header reads. **One fact, one
source**: two surfaces deciding "is this a SaaS install" by different means is SH583's shape, and
it is how the two came to disagree in the first place — `shio-react/test/tenant-nav-gating.test.mjs`
asserts both read `discovery?.multiTenant` rather than merely that each gates on something.

Two things that test checks which a pair of `grep`s would not:

- the route constant is **inside** the conditional, not merely present in the same file — a sidebar
  carrying both the flag and an unconditional item would pass two naive `match` calls and ship the
  defect;
- `multiTenant` is in the `useMemo` dependency list. `useDiscovery` resolves asynchronously, so a
  memo that reads the flag without listing it freezes whatever was true on first paint — which is
  `undefined`, and would hide the item on a *multi-tenant* install instead. The gate has to be able
  to change its mind.

## The console-chrome census — what scopes the bento migration (SH886)

`shio-react/test/console-chrome-census.test.mjs` walks `src/app/console/**/*.page.tsx` and files
each page into one of two eras. The classifier is a single import, and the design system chose it
deliberately: bento ships as the `@viglet/viglet-design-system/bento` **subpath** rather than
through the root barrel, so — in `src/bento/index.ts`'s own words — "an import that says
`@viglet/viglet-design-system/bento` states which era a page belongs to, which is something a
reviewer and a lint can both see."

At the census's first run: **43 pages, 0 on bento** — 39 on `PageHeader`, 38 on `@/components/ui/card`,
3 on `StickySaveBar`, 1 on the sidebar provider, and 3 rendering no chrome this reader knows
(`admin.root.page.tsx` is a bare `Outlet`, `not-found.page.tsx` a `Button`, `visual-editor.page.tsx`
its own frame). Those last three are console-era **by absence**, and an empty marker list is how
that is spelled — drop them and the census silently shrinks by three.

**Two of §LIV.1's three opening figures were wrong.** It said 40 pages on `PageHeader` and 42 on
`Card`; the real numbers are 39 and 38. That grep ran once, by hand, and nothing re-ran it. This is
the whole argument for the file: a migration scoped by a number nobody recomputes has no end, and a
*wrong* number is worse than none, because it reads as measured.

**A named list, not a ceiling.** `STILL_CONSOLE` maps every unmigrated page to the chrome it
renders, so a migration fails the reader *by name* and lands as a diff somebody reviews. A ceiling
would let one migrated slice pay for leaving the next page alone — the same reason
`cli/test/assertion-debt.test.mjs` holds a list. Migrating a page means deleting its line and adding
its name to `MIGRATED`; there is no way to bank progress.

Two guards keep it from going quietly blind. The walk must still find more than 40 pages, or an
emptied directory would read as a finished migration. And **every probe must match something**: a
renamed import path would otherwise turn a probe into a permanent zero, retiring that chrome from
the census rather than from the console — the count would fall with nothing migrated. When the last
page carrying a probe really is migrated, the probe is deleted in that same diff.

## The surface digest — proving a migrated page without opening it (SH896)

`shio-react/scripts/surface-digest.mjs` is the console's analogue of SH101's render digest, and
`pnpm --filter shio-react surface:digest` prints one line per surface. It reads **source, not a
rendered page**: "the instance does not run a browser" is a standing non-goal, and a console page
resolves a router, three contexts and a service call before it paints, so rendering one to prove it
composes would mean standing up most of the app — expensive, and flaky in the way a proof must not
be.

The digest covers three things and nothing else: the **era** (does the page import
`@viglet/viglet-design-system/bento`), the **chrome it composes in source order**, and the
**surface classes** §LIV.11 named — the frosted panel and its lift (`backdrop-blur`, `bg-card`,
`shadow`, `rounded`, `border`, `ring`, …). At the first run: **43 surfaces, 22 distinct digests, 0
on bento.**

```
node scripts/surface-digest.mjs --json > before.json
#   ... migrate a slice ...
node scripts/surface-digest.mjs --against before.json   # exits 1, naming page and field
```

**It is not a pixel claim, and the docstring says so.** A matching digest says the same chrome was
composed in the same order with the same surface vocabulary; it does not say the page looks right.
The direction it buys is the one that matters across 43 surfaces: when a digest moves, something
structural moved, and the report names the page and the field.

**Both failure modes are asserted, because they pull against each other.** A digest that moves on
*everything* — reindenting, a renamed handler, a new comment — is noise, and a reviewer learns
within a week to re-baseline without reading it, which is worse than no proof because it looks like
proof. A digest that moves on *nothing* goes green across the migration it exists to witness. So
`surface-digest.test.mjs` asserts stability under reformatting, comments and renamed locals, **and**
movement on a swapped component, a reordered header, an era change and a changed surface class.
Testing the hash function would have caught neither.

One deliberate sharp edge: the sort is **codepoint, never `localeCompare`**. The digest hashes that
order, so a locale-dependent comparator would hash one page to two values on two machines. The lint
that asks for `localeCompare` is right about display lists and wrong here; the comparator is stated
in the file with that reason beside it.

## The bento layer, installed and proved here (SH887)

`@viglet/viglet-design-system/bento` is now imported by this console, and one page —
[`src/app/bento/bento-vocabulary.page.tsx`](../../shio-react/src/app/bento/bento-vocabulary.page.tsx)
at the top-level route `/bento-vocabulary` — composes 23 of the layer's 35 runtime exports.
It is a **harness, not a surface**: no nav entry, no service, no request, and it is deleted
when Block AR finishes. `src/index.css` takes `bento.css` as a second import, because the
package ships the layout maths and the stylesheet as separate entries and a consumer that
imports only the components gets working, tested, completely unstyled markup.

`test/bento-vocabulary.test.mjs` holds the two halves as **named lists**: what the page
renders, and what it defers with the task that owns each (the shell to SH888, the nav-driven
surfaces to SH889, the three whole-screen shapes that own a hero and the save-bar morph to
SH892/SH899/SH901). Their union must equal the package's runtime export set, read out of the
shipped `index.d.ts` rather than hardcoded — so a design-system release that adds a component
fails here by name instead of leaving the harness quietly proving less than it used to.

### The three things looking at it found

**A page outside the console shell must paint its own ground.** `<html>` carries `dark` from
`ThemeProvider`'s default while `<body>` keeps `shio-light-boot` — the design system's
boot-loader plugin picks light when nothing is stored under `shio-ui-theme`, which is every
first visit, and nothing removes the class once React mounts. Every console page hides it by
painting `bg-background` over the viewport. The harness renders `bg-background text-foreground
min-h-screen` for that reason and not for decoration; **SH918** fixes the disagreement itself,
and SH888 depends on it, because a second parallel root inherits exactly this.

**A top-level route needs its segment reserved.** `readTenantSegment()` treats every unreserved
first path segment as a tenant, so `/bento-vocabulary` would set `TENANT_SEGMENT` to itself and
prefix every `ROUTES.*` the page links to with a tenant that does not exist. The page still
renders, which is what makes it worth asserting rather than noticing.

**The package translates its own chrome, in both languages.** Measured, not assumed: the same
page renders `Unsaved changes` under an `en-US` browser and `Alterações não salvas` under
`pt-BR`, because `registerVigTranslations(i18n)` wires the package's bundle into this console's
resources. SH891's roadmap line said the opposite and has been corrected — what is left for
Shio is the half the authoring contract says the package will never hold: the nav array's own
labels and the palette entries built from them.

### What it deliberately does not do

Specimen copy lives in `bento-vocabulary.fixture.ts`, not in the `.tsx`. `console-english.test.mjs`
is right to fail a curator-facing page that hardcodes an English sentence and would be wrong
about this one — these are specimen strings naming the components being proved, read by nobody
in Portuguese. Separating them keeps that lint's `UNTRANSLATED` exemption map short enough to
stay readable, which is the only thing it has going for it. A migrated Block AR surface goes
through `t()` like every other page.

## Shio's accent is a token, and the rest is a named debt (SH890)

The console's orange is declared **once**, on `:root` in
[`src/index.css`](../../shio-react/src/index.css), as the four source tokens VDS23 made the
whole re-keying contract:

| Token | Value | Was |
|---|---|---|
| `--vg-accent-from` | `oklch(70.5% 0.213 47.604)` | `orange-500` |
| `--vg-accent-to` | `oklch(76.9% 0.188 70.08)` | `amber-500` |
| `--vg-accent-text` | `oklch(64.6% 0.222 41.116)` | `orange-600` |
| `--vg-accent-text-dark` | `oklch(75% 0.183 55.934)` | `orange-400` |

Everything else derives from them in the package's preset — the chip tint, the ring, the
accented label, the solid fill, the focus ring and both hover steps. **Set them on `:root`,
never on a wrapper.** A custom property substitutes its `var()` references where it is
*declared*, so the preset's derived tokens resolve against whatever `--vg-accent-from` cascades
to **on the root element**; a later `:root` rule (this one) re-keys the whole family, and a
wrapper re-keys only what a utility mixes inline and leaves the rest at the package's blue.
Verified at runtime rather than argued: with the override in place `--vg-accent-fg` resolves to
the orange dark-text token under `.dark`, and `vg-accent-chip` paints
`linear-gradient(… oklab(0.705 0.143615 0.157301 / 0.2), oklab(0.769 …))`.

**How to spell the accent** — none of these need a `dark:` twin, because the tokens carry it:

| You want | Use |
|---|---|
| The tinted icon chip with its ring | `vg-accent-chip` + `ring-1` |
| Accented text or a glyph | `vg-accent-text` |
| A flat tint behind a bar or panel | `bg-[var(--vg-accent-surface)]` |
| A filled gradient (primary action) | `vg-accent-solid` |

`--vg-brand-*` is a different token family and is **not** Shio's accent: that is Viglet's
corporate orange from viglet.org, shared by every product's footer and app switcher.

### The debt, and why it is a list

The grep behind SH890 found **97 orange utilities across 24 files** — orange is Shio's brand,
so every page that wanted to look like Shio copied a class string. This task converted the two
things that were shared or duplicated: `page-header.tsx`'s icon chip (a 150-character class
string written out twice) and the six admin save-bar tints, which were the byte-identical
`bg-orange-50 dark:bg-orange-950/30` in six files. That leaves **17 files, 61 distinct
utilities**, held by name in `test/accent-token.test.mjs`.

A named map and not a ceiling, for the reason the chrome census gives: a ceiling lets one
migrated page fund leaving the next alone. A Block AR migration deletes its file's line in the
same commit; a page that adds an orange fails by name. `amber` is deliberately not counted —
that is the semantic warning colour and it means "careful", not "Shio".

## The chrome adapter, and its two silent failure modes (SH892)

The post editor, the post-type editor and the site editor wrap their sections in
`AdaptiveSectionCard` instead of `@/components/ui/card`. The adapter renders a console card
today and a frosted bento section when a `SectionCardChromeProvider` above it says so, and
**console is the default** — so adopting it changed nothing visible, and SH899/SH901/SH902 opt
in by adding a provider rather than by rebuilding 1,600 lines of the most defect-prone code in
the console.

Two rules, both of which produce a page that compiles, passes every source scan, and does
nothing. Both were in this task's own first draft, and both were found by *rendering* the built
package with `react-dom/server` — [`test/chrome-adapter.test.mjs`](../../shio-react/test/chrome-adapter.test.mjs),
which is the one reader here that renders rather than reads. That is affordable because the
adapter is a wrapper, not a page: no router, no contexts, no service call, about a millisecond.

**1. A section with no header never re-skins.** The bento branch is entered only when
`Children.forEach` recognises a `Header`/`StaticHeader` child; anything else falls through to
the console card, deliberately, so a section built some other way cannot lose its content
silently. The consequence for a consumer is the opposite of obvious: an adopted section with no
header is a **no-op** that looks migrated in every import scan. The post editor's card carried
an empty `CardHeader`, so it gained a real one (`content.fields` — identity stays in the hero).

**2. `Header` and `Content` must be DIRECT children.** `Children.forEach` does not descend. Wrap
`Content` in anything — `<Tabs>` was this task's first draft for the post-type details card — and
the bento branch finds a header and no content, and renders an **empty section**: the whole form
gone, in whatever later commit switches the chrome on, nowhere near the commit that caused it.
`Tabs` belongs *inside* `Content`.

### The era classifier had a hole this walked through

`console-chrome-census.test.mjs` decided a page's era by "does it import the bento subpath",
which is what the design system's own source says the subpath is for. `AdaptiveSectionCard` is
the one export written to be imported from a **console** page, and the package exports it from
that subpath and nowhere else — so adopting it made all three editors read as *migrated* in a
single commit, shrinking the debt by three pages nobody had touched. The classifier now excludes
three names (`AdaptiveSectionCard`, `SectionCardChromeProvider`, `useSectionChrome`) **by name**,
so a page importing the adapter *and* `BentoHero` is still migrated. The exclusion carries its
own vacuity test, because an over-broad exemption fails in the direction that reads as a
migration which never started.

Census after this task: **43 pages, 0 on bento** — `Card` 38 → 35.

## Re-declaring a shared component fails the build (SH894)

The design system emits its export list as `@viglet/viglet-design-system/exports.json` and ships
`scripts/check-duplicates.mjs` to read it. That gate now runs inside this repository's own JS
suite — [`test/no-duplicate-components.test.mjs`](../../shio-react/test/no-duplicate-components.test.mjs)
spawns it — so a component declared here that the package already exports fails `pnpm test` and
`suites.yml`, and the failure message carries **the import that replaces it**. A check reachable
only by a second command is a check that runs when somebody remembers.

A one-line re-export shim is the sanctioned pattern and is not a duplicate. A deliberate
collision is declared in the file itself:

```
// viglet-ds-allow-duplicate <Name> -- why this one is not the shared one
```

**The reason is the point.** A gate with no way to say "this one is mine" is a gate somebody
deletes, and five of the seven findings this gate opened with were exactly that case.

### What the first run found

§LIV.9 argued from "zero forks" across the 24 files in `components/ui`. That was true of
`components/ui`. Across `src`, which is what the gate scans, there were **seven**:

| Finding | Resolution |
|---|---|
| `mode-toggle-sidebar.tsx` | **Deleted.** A fork of `ModeToggleSidebar` sitting beside its own shim — `mode-toggle.tsx` already re-exported the shared one — with zero importers. |
| `dialog.delete.tsx` | **Shimmed.** 91 lines that had drifted behind the shared dialog, rendered by 18 pages. Same i18n keys, so no text changed; the shared one adds the `blockedBy` branch that says what is still referencing the thing you are deleting. |
| `AppFooter` | Exempt — fetches Shio's build version; the package's is presentational. |
| `NavMain` | Exempt — carries SH207's `badge`/`badgeLabel`, which the package's `NavItem` has no field for. Exactly the case §LIV.9 predicted. |
| `PageHeader` | Exempt — breadcrumbs, tenant switcher, app switcher, language switcher, two breakpoints. SH910 deletes it. |
| `UserProvider`, `useCurrentUser` | Exempt — typed on `ShCurrentUser` and bound to `ShUserService`; the package's is generic over `VigUser` with `fetchUser` injected. |

### Never pass this gate a positional root

Its argument filter is `i !== manifestFlag + 1`, and `indexOf` returns `-1` when `--manifest` is
absent — so `manifestFlag + 1` is `0` and **the first positional root is always discarded**.
`check-duplicates src app` scans only `app`; `check-duplicates no-such-dir` scans `./src` and
exits 0. `check-duplicates src` masks it exactly, because the argument it drops is the directory
it then defaults to. The reader here steers with `cwd` and passes no root. **SH919** fixes it
upstream; the workaround is carried in a consumer, which is the wrong place for it.

## The theme is declared once, because two things decide it (SH918)

[`src/theme.ts`](../../shio-react/src/theme.ts) holds `SHIO_THEME_STORAGE_KEY`,
`SHIO_DEFAULT_THEME` and `SHIO_LIGHT_BOOT_CLASS`, and **both readers import it** —
`vite.config.ts` (which is TypeScript, so it imports the module directly) and `App.tsx`.

Two pieces of code answer "what theme is this", seconds apart, on different objects:

1. The design system's **boot-loader script**, injected into `<head>` by its Vite plugin, runs
   before any module. It reads `localStorage[storageKey]` and — finding nothing — falls back to
   `prefers-color-scheme`, then stamps `shio-light-boot` on `<body>`, painting `rgb(250,250,250)`
   and a salmon radial gradient.
2. **`ThemeProvider`** stamps `dark`/`light` on `<html>` when React mounts, and every
   `--background` / `--foreground` token resolves from that.

They disagreed on **every first visit from a light-preference machine**: the boot script honoured
the OS, the provider hard-coded `dark`. Nothing removed the boot class afterwards, so `<html>`
said dark over a near-white `<body>` for the whole session.

Two changes, and both are needed:

- **A seed script, injected `head-prepend`** (so it runs before the boot loader), writes the
  default into storage when nothing is stored. Storage — not a shared constant — because it is
  the one input *both* readers already consult, which makes agreement structural. The plugin
  exposes `storageKey` but no `defaultTheme`, so this is the only seam.
- **`main.tsx` clears the boot class** after React replaces `#root`. Seeding fixes first paint;
  it does not fix a reader who then toggles to dark, which would leave the light ground behind.
  The removal sits *inside* the `?loading=1` guard — that mode holds the loader visible on
  purpose, and stripping the class it is styled with would break the one thing it exists to show.

### Why the reader runs both scripts

Nothing about this defect is visible in either file — the boot script is a string inside a
plugin, `ThemeProvider` is a component, and the only place they meet is a browser.
[`test/shio-theme-seed.test.mjs`](../../shio-react/test/shio-theme-seed.test.mjs) pulls the real
boot script out of the plugin by calling its `transformIndexHtml`, imports the real seed from
`src/theme.ts`, and runs them in order in `jsdom` with a stubbed `prefers-color-scheme`. Its
second test removes the seed and asserts the split **comes back** — without that, a green run
proves only that something passed.

Two things worth stealing from it: **node strips types on import**, so an `.mjs` test can
`await import("../src/theme.ts")` and exercise the exact text that ships (the first draft scraped
the source with a regex and got the template's uninterpolated `${…}` placeholders); and jsdom
implements no media queries at all, so `window.matchMedia` must be stubbed — with the *failing*
preference, or the test passes over the only case that was ever broken.

Verified in a browser under both OS preferences: `<body>` carries no boot class and paints
`rgb(10,10,15)` under `html.dark`, where it used to paint `rgb(250,250,250)`.

## The bento route tree, beside the console one (SH888)

`/bento` is a **sibling** of `/console`, not a child.
[`bento.root.page.tsx`](../../shio-react/src/app/bento/bento.root.page.tsx) mounts the package's
whole shell — `BentoNavRail` down the left, `BentoUserMenu` in the header, `BentoBackToTop` at
the corner — and **no provider between them**. `console.root.page.tsx` wraps everything in a
`SidebarProvider` and its `PageHeader` calls `SidebarTrigger`; the two cannot nest, which is why
a page-at-a-time migration in place would leave the console broken in the middle of each of 43
slices. Both trees stay mounted for the length of Block AR, each surface moves under its own
task, and **SH909** removes whichever shell loses — on the roadmap precisely so the temporary
state cannot become permanent by nobody deciding.

[`bento-nav.ts`](../../shio-react/src/app/bento/bento-nav.ts) is the product's own map, which the
package will not hold: "an entry appears when its route does." The rail draws **sections only** —
it filters `groups` to those carrying an `areaRoute` — so a section without one is invisible,
silently. `BentoNavItem` exists for the command palette, which SH889 wires; items are declared
here so the rail and the palette cannot list a surface twice. `fallbackRoute` and `areaRoute`
point at the **console** paths on purpose: each AR task repoints its own entry, and until then
the rail takes you to the page that exists.

### Two things that only a render says

**The shared user menu needs the *package's* `UserProvider`.** The shell came up entirely blank
behind the error boundary with `useCurrentUser must be used within UserProvider`. `BentoUserMenu`
reads the design system's context, and SH894 established Shio's provider as a deliberate local
variant — so it is a **different context object** and satisfies nothing. `tsc` is silent (both
are called `UserProvider`, both take children); so is every import scan. The seam is `fetchUser`,
and `ShCurrentUser` is structurally assignable to `VigUser`. **SH920** carries the consequence:
the two trees now mount different user contexts, and all fourteen AR surfaces cross that seam.

**The header is `sticky`, not `fixed`.** `BackendStatusProvider` renders its "server unavailable"
bar as `sticky top-0 z-50` from `main.tsx`, above this tree in the flow. A fixed header sat on
top of it and covered the retry button with the avatar — visible only while the backend is down,
which is exactly when that button matters. Sticky lets both stack.

`test/support/source-text.mjs` now holds the comment stripper these readers need. It has caught
three of them (SH823, SH890, SH888) the same way: a reader asserting "this file must not say X"
failing against the paragraph explaining why it does not. That is not incidental here — readers
in this repository carry their reasoning in prose beside the code, so any such scan runs over a
file very likely to *discuss* X.

## Every surface declared once, for both readers (SH889)

[`bento-nav.ts`](../../shio-react/src/app/bento/bento-nav.ts) declares **17 surfaces in 2
sections**, and the rail *and* the command palette both read it. The rail takes
`useBentoNav()` (grouped), the palette takes `useBentoNavItems()` (flat). A palette built from
its own array would look completely fine right up to the release where one of them lists a
surface the other does not — that is the defect §LIV.4 was filed against.

Get this shape right early: it is the file each of **Block AR's fourteen** surface tasks appends
to, so a shape settled after five have shipped is a shape five tasks have to be reopened for.

| Field | Whose | Notes |
|---|---|---|
| `titleKey` | reused | Always a key the console sidebar already renders — the authoring contract's rule that the same entity in two chromes is the same entity |
| `descriptionKey` | new (`nav.desc.*`) | No console equivalent: the sidebar shows a label, a palette row and a hub tile show a sentence. `en` + `pt` together |
| `fallbackRoute` | required | Where the surface lives **today** — a console path, for all 17 |
| `bentoRoute` | absent until moved | `bentoNavTarget()` prefers it, so **a migration is one line here** |
| `tone` | grouped by kind | blue content · indigo model · violet delivery · amber identity · slate plumbing — a new entry has an obvious answer rather than a free choice |
| `requires` | Shio's own | On `ShioNavItem`, not `BentoNavItem`: the package cannot read a feature model and should not try |

`useBentoNav()` is a **hook** because visibility is answered at runtime — `useDiscovery()` is how
the console learns whether `shio.multi-tenant` is on, and SH46's finding was that an ungated
tenants entry offers every single-tenant install a page for managing tenants it does not have.
Verified: the palette lists **16** of 17 on a single-tenant instance.

### What the descriptions are actually for

Not the row text. The palette renders the title plus an external-link marker for anything without
a `bentoRoute`; `descriptionKey` feeds its **fuzzy search index**. Measured in a browser rather
than assumed — typing `restorable` (a word that appears only in `nav.desc.trash`) returns
**Trash**, `credentials` returns **API Tokens**, and a nonsense term returns nothing. That is
what a description buys, and it is why they are worth writing well.

The palette's keybinding is the product's — the package takes `open`/`onOpenChange` and nothing
else. Ctrl/Cmd+K toggles, and a bare `/` opens **only when the reader is not already typing**: a
`/` swallowed inside an input is the one way a search shortcut makes a product worse than not
having one.

## The bento chrome's strings, and the reader that keeps them resolving (SH891)

**The roadmap line was nearly all wrong, and measuring is what said so.** §LIV.6 was filed as
"bento chrome strings have no Shio translations, so a shared rail and palette would render raw
keys", implying a sweep. The layer calls **54** keys. **53 already resolved in both languages** —
`registerVigTranslations(i18n)` wires the package's own bundle into this console's resources, so
Shio inherits them. Exactly one did not: `home.title`, the rail's Home button, which the package
does not carry because a product's home is a product's own word. Added as `Home` / `Início`.

It would not have rendered a raw key either. The rail calls `t("home.title", { defaultValue:
"Home" })`, so a Portuguese console rendered the English word **Home** — the failure mode worth
naming, because a raw `home.title` on screen is something anybody reports and an English word in
a Portuguese UI is something most people scroll past.

So the sweep was not the deliverable.
[`test/bento-i18n.test.mjs`](../../shio-react/test/bento-i18n.test.mjs) is: the package ships the
layer, so the set of keys it renders changes **without anything in this repository changing**,
and the only thing that would otherwise notice is somebody reading the console in Portuguese.

### Two things it does differently, both learned the hard way

**It resolves through a real i18next instance** rather than comparing flattened objects. The
first draft's model of the merge was wrong: `registerVigTranslations` adds the package's bundle
**per top-level namespace, and only where the consumer does not already have that namespace** —
and Shio's bundles are a mix (`common.json` is flat dotted keys, `home.json` nests under a `home`
object). Whether a flat `"common.cancel"` counts as owning the `common` namespace, and whether
i18next's `deepFind` then reaches a nested one underneath it, are not questions to answer from
memory. The test builds an instance, registers the real bundle with the real function, and asks
`t()`.

**Its key extraction is deliberately broad** — any dotted lowercase literal in `dist/bento.es.js`,
not anchored on `bento.` or on a `t(` call. The bundle is minified so `t` is a renamed letter;
and anchoring on the prefix missed 18 of the 54 including the whole `forms.*` group and
`home.title` itself — the one key that was actually missing. Measured: **54 literals, no false
positives**, because a bundle that size has no other dotted lowercase strings.

One assertion is worth stealing: `home.title` must read **differently** in the two locales. Its
fallback was already the English word, so a "translation" that copies the English value restores
the defect while looking fixed.

## The bento accessibility rules, checked rather than remembered (SH893)

The package runs its stories under axe on every push — that covers **the package's** components
and says nothing about how a consumer composes them.
[`test/bento-a11y.test.mjs`](../../shio-react/test/bento-a11y.test.mjs) holds three rules over
Shio's own source:

1. **Icon-only controls need an accessible name.** `aria-label`, `title`, an `sr-only` span, or
   readable text. A bare `<svg>`'s own label is not reliably announced, and `getByLabelText` will
   not match it either.
2. **A state ARIA attribute is a string literal** — `aria-selected={active ? "true" : "false"}`,
   never `{active}`, never a bare `aria-hidden`. Content attributes (`aria-label` and friends)
   are excluded: an expression there is correct. This console had **zero** violations when the
   rule arrived, which is the easiest state to keep and the easiest to lose.
3. **A keyframe Shio declares is named in the reduced-motion guard.** Zero today — every
   animation on a bento surface comes from `bento.css`, whose guard the package asserts — so the
   rule is exercised against synthetic CSS in its own test. A rule whose body is `if (nothing)
   return` is a rule nobody has ever seen run.

### The measurement had a bug, and it is the reason `jsx-elements.mjs` exists

The first pass reported **3** unnamed icon-only buttons out of 50 and read as almost clean. The
real number was **34**. `/<Button\b([^>]*?)>/` ends the opening tag at the `>` inside
`onClick={() => openBrowserAt()}`, so the "body" begins mid-prop and carries the handler's
identifier — and the check "does the body contain readable text?" answers *yes*, because
`openBrowserAt` is letters. Two adjacent unnamed buttons in one file, one reported and one not,
differing only in whether their handler was an arrow.

[`test/support/jsx-elements.mjs`](../../shio-react/test/support/jsx-elements.mjs) scans forward
tracking brace depth and quotes instead. **Use it for any JSX census here** — a gate that
under-reports is worse than no gate, because a green run reads as coverage.

### Six fixed, twenty-eight declared

The six in **shared components** are fixed, because the migration keeps them: the permissions
dialog and three of the fifteen form widgets, which SH892 exists precisely to avoid rewriting
(`date.widget.tsx` had no i18n at all and gained the hook; `common.clear` is new in both
locales). The remaining 28 are console pages Block AR rewrites, held by name — each AR task
deletes its own line. **A shared component may never appear in that list**, and the gate asserts
it separately: a name missing from one is missing on every page that renders it.

## The one script that opens a real page now opens a bento one (SH897)

`e2e/console-check.mjs` is the only thing in this repository that drives the console **in a
browser**, and every route it knew was a console route. Left alone it would have run green
through all of Block AR without visiting a single migrated surface — worse than not running,
because a green check is counted as evidence. It now probes `/bento`, and Block AR adds one line
per migrated surface.

A `bento: true` route goes through `checkBentoSurface`, which asks four things of the **rendered
page**: a hero heading, a frosted surface that is actually frosted, a nav rail, and no sidebar
provider anywhere inside the tree (plus the rail gutter, without which content renders under the
fixed rail). Findings go into `probe.missing`, which is what already decides `ok`.

### The frost check was vacuous, and deleting the stylesheet is what proved it

The first version accepted *either* a `backdrop-filter` or a non-zero `border-radius`. That
second clause made it pass on nothing: `rounded-3xl` is a Tailwind class on the component itself,
so the radius is 24px whether or not `bento.css` is loaded. Verified by removing the import and
re-running — the page rendered as flat, borderless, unstyled divs, `bento-root.png` showed it
plainly, and the probe reported **ok**.

`backdrop-filter` alone now. With the stylesheet: `blur(18px) saturate(1.8)`. Without: `none`.
**Check a new probe in both directions** — a browser check that cannot fail is the most expensive
kind of green there is, and this one had to be broken on purpose to find out.

### The morph has no subject yet, and says so

`checkSaveBarMorph` asserts nothing is fixed before scrolling and a bar appears after. No surface
carries one until SH901/SH902 move an editor, so it prints
`morph: no surface carries a save-bar morph yet (SH901)` on every run rather than returning a
quiet pass. That distinction is this file's own history: SH238/SH659 found that a probe which
skips keeps `ok: true`, counts toward "N/N probes ok", and leaves the exit code at 0.

### What the first live run found, which was not about bento at all

`post-edit:File` failed. `/agent/find` returns static files alongside posts — correctly; they are
content and they are addressable — but the row lives in `/api/v2/staticfile/{id}` (200), not
`/api/v2/post-unified/{id}` (**404, empty body**). The probe list no longer opens a post editor
for a static file, and the bare 404 is filed as **SH921**: P6 says every 4xx teaches, and this
one is reached by following an id a sibling endpoint just handed you.

**Running it locally:** `SHIO_USER=admin SHIO_PASSWORD=viglet` — the console login is
`admin`/`viglet`, not the `admin`/`admin` the script defaults to.

## One user provider, two hooks, one fetch (SH920)

`@/contexts/user.context` no longer implements a provider — it **wraps** the package's and
republishes its value as `ShCurrentUser`. One mount satisfies both `useCurrentUser` hooks, and
both shells import Shio's.

**The failure it closes has no type error.** Shio declared its own `UserProvider`/`useCurrentUser`
pair, and a local variant of a context is a *different context object*. Mounting Shio's around a
shared component that reads the package's satisfies nothing: `BentoUserMenu` threw and blanked
the whole bento shell behind the error boundary. `tsc` is silent — both are named `UserProvider`,
both take children, and the mismatch exists only at runtime in `useContext`. So is every import
scan. SH888 worked around it by mounting the package's provider in the bento shell alone, which
put the same trap in front of each of Block AR's **fourteen** surfaces.

**Wrapping, not folding.** `ShCurrentUser` requires `privileges`; `VigUser` leaves it optional,
and `authorization.service.ts` reads it to decide what a curator may do — folding the types would
push a guard into a privilege check, the one place a wrong answer is worst.

**What Shio gave up by not implementing it is the good part.** The 24 lines replaced were
`shUserService.get().then(setUser)`: no catch, so an expired session became an unhandled
rejection leaving `user` an empty object; and no request sequencing, so two overlapping
`refreshUser` calls let the slower one win. The package's provider handles both and carries
`status`/`error`, passed through here.

`VigUserStatus` is declared in the package's context module and **not re-exported from the root
barrel**, so the type is derived as `ReturnType<typeof useVigCurrentUser>["status"]` — a
hand-written copy of a three-member union is a copy that goes stale silently.

Measured in a browser: both shells render, no errors, and `/v2/user/current` is requested
**twice** on each — which is React `StrictMode` double-invoking in dev, not the nesting. Two and
not four is the number that matters.

### Two process notes this task produced

**SH888's assertion went red on this commit, and that was the point.** It demanded the *package's*
provider by import path, because that was the only thing which satisfied the menu when it shipped.
It now asserts the narrower invariant — a provider is mounted, it is Shio's, and the shell does
not configure it — so the decision was named rather than drifted past.

**The assertion-debt gate cannot fail on the run before a ship.** It reads the ledger, and `ship`
is what writes it — so `pnpm test` → `ship` → commit leaves SH527's check to fire on the *next*
task's run. SH897 was caught that way. Run the JS gate once more after `ship`, before committing.

## The duplicate gate's root argument, and a workaround with an expiry date (SH919/VDS78)

`check-duplicates.mjs` computed the index to skip as `manifestFlag + 1`, and `indexOf` answers
`-1` when `--manifest` is absent — so `-1 + 1` is `0` and the filter discarded **argument zero**,
the first positional root, on every invocation without a manifest. `check-duplicates src app`
scanned only `app`; `check-duplicates no-such-dir` scanned `./src` and exited 0.

It hid twice, and the second reason is the transferable one. The documented call is
`check-duplicates src`, where the dropped argument is the same directory the default then falls
back to — so the common case is correct by accident. And **every test in the package's own
`check-duplicates.test.ts` passes `--manifest`**, which puts the flag at index 1 and makes the
arithmetic land as intended. The suite exercised only the configuration where the off-by-one
cancels out. *When a test helper always supplies an optional argument, the branch without it is
untested by construction.*

Fixed upstream as **VDS78**, with two tests that run without `--manifest` — verified by reverting
the parse, which fails exactly those two and leaves the other eleven green.

### The workaround expires by assertion, not by comment

Shio spawns the gate from `node_modules`, so the **installed** version decides whether
`NO_POSITIONAL_ROOTS` is still needed — not a fix in a checkout. `no-duplicate-components.test.mjs`
therefore asserts the bug is **still present**, which is a strange thing to assert on purpose and
is the whole point: the day an installed release carries VDS78 it goes red, and its message is the
three-step instruction to delete the workaround, delete the test, and close SH919's open half.

A comment saying "remove this later" expires silently. A test does not.

## Nothing interactive inside anything else interactive (SH922)

A fourth rule in `bento-a11y.test.mjs`: no interactive element nested in another. `<a><button>`
is invalid HTML, and browsers resolve it however they like. What it cost a reader was concrete —
on the static-file list, **two tab stops on every one of 169 rows**, the second announcing nothing
because only the anchor carried a name.

**SH893's census could not see it, and that is the interesting part.** That reader asks whether an
icon-only control has an accessible name; here the anchor *had* one. A scan of either element
alone finds nothing wrong — the defect is in the relationship between them.

### Interactivity is decided by the import, never by the name

The first draft listed `Link` as interactive and reported a second finding in
`html-editor.widget.tsx` — which was **lucide-react's `Link` icon**, sitting inside a toolbar
button exactly as it should. `Link` is a router element in one file and a glyph in the next, and
so is anything a product happens to name twice. Each conditional name now counts only where *that
file* imports it from something that makes it interactive. `asChild` is excluded too: it means the
wrapper delegates its element to the child, so there is one element rather than two — missing that
would report every correct shadcn link-button in the console.

### What the two instruments each found

The browser measured the cost: 169 rows, 169 nested pairs. The source scan found the **second**
instance, in a detail panel the probe never reached because it sits behind a selection. *A run
measures what a rule is worth; a scan finds the rest of it.* Both are fixed by the same decision —
the anchor is the control, so it carries the button's classes and the button goes.

Verified after: 169 rows, **0 nested pairs**, two focusables per row for two real actions.

## The first migrated surface, and the instruments that could not see it (SH898)

The console home is on the bento shell at `/bento`:
[`src/app/bento/home/home.page.tsx`](../../shio-react/src/app/bento/home/home.page.tsx). Every
fetch, service and i18n key is the console page's — what moved is the chrome. `BentoHero` replaces
`PageHeader` and an `<h2>`, `BentoCountTile` the two counts, `BentoTile` the six quick actions,
`BentoEmptyState` an activity list with nothing in it.

| surface | era | chrome |
|---|---|---|
| `console/home/home.page.tsx` | console | PageHeader, Card ×3 |
| `bento/home/home.page.tsx` | bento | BentoHero, BentoSection, BentoCountTile, BentoTile, BentoEmptyState |

### What the first migration actually found

`surface-digest.mjs` reported **"No surface moved (43 compared)"** for the whole of this task, and
it was right: it walked `src/app/console`, nothing there had changed, and the new page was in
`src/app/bento`. SH888 chose a parallel tree, so **a migration adds a surface rather than editing
one** — and every instrument that measured "the console directory" was measuring the half that
does not move. That is what building the first surface is for.

The digest now surveys both trees and keys them under `src/app`. A saved baseline from before
reports every surface `gone` and every surface `new`; re-take it once.

**The census was not wrong, and now says so.** SH886 expected `MIGRATED` to fill one name at a
time; under a parallel tree it stays **empty forever**, because a name would appear there only if
somebody imported bento *into a console page* — the arrangement §LIV.3 says cannot work. Its
docstring now states that, so the next reader does not wait for a number that will never move.
Progress is `surface-digest` (era per surface) and `bento-nav.test.mjs` (`n/17` pointing at a
bento route).

### Three things the page itself decided

**The counts wait rather than answering zero.** `BentoCountTile` takes a `number`, and the obvious
`?? 0` puts "0 Sites" on the first thing a curator sees after login. Zero is a real answer to that
question and a wrong one.

**A required prop is not an invitation to repeat the heading.** The first draft gave all six
action tiles `eyebrow={t("home.quickActions")}`, rendering "QUICK ACTIONS" six times under a
heading already saying it. They carry the *area* each action leads into instead.

**A section with nothing to add says nothing.** The counts section was titled `home.statSites` —
the name of one of the two counts in it — and described with the hero's own subtitle, putting the
same sentence on screen twice forty pixels apart. It is `home.overview` now, with no description.

The badges lost six hardcoded colour strings to `bentoChipClass`, so SH890's re-key reaches them.
`teal` is not a tone, so `RESTORE` reads violet — the one deliberate visual difference.

## The site slice, and the rule that makes a save bar do something (SH899)

Four surfaces under `/bento/content`: the list (`BentoListPage` + `BentoEntityTile`), the editor
(`BentoEntityShell` — the first in this console), and create/import (`BentoFormHero`). The four
console pages are untouched until SH909, which is what makes the slice reversible.

### The defect a run found and nothing else could

`BentoEntityShell` renders its Save as `<GradientButton type="submit" form={formId}>`. That
submits **an HTML form with that id, and nothing else**. The first draft returned a
`BentoFormSection` from the render prop with no `<form>` around it — so the page rendered
perfectly, the hero showed Save Changes and Cancel, and clicking Save issued **no request, logged
nothing and threw nothing**.

`tsc` cannot see it: `formId` is a string and the shell has no way to check a form with that id
exists. Typing into a field, clicking Save, reloading and reading the value back is what found it.

**`onUpdate` does not cover this.** The shell calls that when the *hero identity* is edited (type
→ blur → autosave), so a page can have a working hero and a completely dead body. Every remaining
Block AR entity editor composes this same pair, so the rule is asserted rather than remembered: a
shell that names a `formId` must contain a `<form>` with that id **and** a submit handler that
reaches a service.

### Two era classifiers, one taught

SH892 taught `console-chrome-census.test.mjs` that `AdaptiveSectionCard` is era-neutral — and did
not teach `surface-digest.mjs`. SH899 is where that cost something: the **console's own**
`site.edit.page.tsx` came back `bento`, because SH892 had given it the adapter seven tasks
earlier. Both now hold the same three names, and a test asserts the two lists match — not by
sharing a module, because the digest is a script the console runs on its own and a script
importing a test file is the worse dependency.

### What the list settles for the ten that follow

| decision | why |
|---|---|
| Actions on the entity, not the row | the console row hid export/edit/view/delete behind `opacity-0 group-hover:opacity-100` — **four actions reachable only by a mouse**. They are `extraActions` on the shell now: focusable and announced. |
| A failed load is not an empty list | `undefined` means "loading" to the package's gate, so a rejected query must set `[]` **and** an error, or the page spins for ever. The console page rendered its empty state for both. |
| The filter goes in `headerAction` | `BentoListPage` has no search by design. Dropping it would have been a regression on the console list most likely to grow. |
| The Sites/Tasks tab strip is gone | `tasks` rendered `content.noPendingTasks` in every state since it was written. A tab that is always empty teaches a curator not to look at tabs. |

`new` and `import` are declared **before** `:siteId`, or the parameter route matches them first
and the editor tries to load a site called "new".

A third repetition of SH898's lesson, in three of the four files: a section titled with the name
of one field it contains, or described with the sentence the hero above already says. Both props
want a string, and the nearest string to hand is almost always one already on screen.

## The migration shape depends on the surface (SH900)

SH898 and SH899 migrated by **adding** a surface beside the console one. That is right for a home
page of 154 lines and four site pages of 100–200. `object.children.page.tsx` is **965 lines** —
twenty state variables, three view modes, selection, pagination, copy/move — and exactly **four**
of them were chrome: a `PageHeader` and three `Card`s.

Copying it would have duplicated 960 lines of the most defect-prone code in the console so that
four could differ, and every fix would then need landing twice until SH909. So the browser is
written **once**, and the route decides its chrome:

```tsx
<Route path="content/object/:objectId" element={
  <SectionCardChromeProvider chrome="bento"><ObjectChildrenPage /></SectionCardChromeProvider>
} />
```

`AdaptiveSectionCard` covers the three panels;
[`PageHero`](../../shio-react/src/components/page-hero.tsx) covers the
header — `PageHeader` or `BentoHero`, chosen by `useSectionChrome()`. **A small entity-shaped page
is worth writing twice, because what changes is its whole structure. A large working surface is
not, because what changes is its chrome.**

`PageHero` lives in `components/` and not beside a page, deliberately: it imports
`BentoHero`, a genuinely bento-era name, and keeping that import on this side of the boundary is
what lets both era classifiers stay honest about the pages that use it.

### A third way to have no chrome

The census now records `content/object.children.page.tsx` as `[]`. The other three empty entries
(`admin.root`, `not-found`, `visual-editor`) render no chrome because they never did; this one
reached the same state by **migrating**. It composes no console chrome of its own and is still a
console-era page — the same component renders bento under the provider.

### A folder is nothing but identity

`ShFolder` is `name` + `description`, both hero fields, so the editor's form body is **empty** —
precisely the shape that gave SH899 a dead Save button. `autosaveOnly` is the flag the package
already had: `showSaveSubmit = !(autosaveOnly && !isNew)`. Verified in a browser: **0 save
buttons, 0 forms**, and the hero's inline edit issues a `PUT` that survives a reload.

### One thing this task nearly changed and should not have

Moving the browser's two site actions into the adaptive header, I restyled `View Site` from its
green to an outline button. With one component serving both chromes, that is a **visible change
to a console surface this task is not migrating** — the whole point of the arrangement is that
the console one keeps looking the same. Restored, with the note that the hue is unresolved for
bento, where a primary action is a tone.

## The heaviest forms, and an adapter that spans a 1:2 mapping (SH901)

The post editor and the new-post flow are the **same components** the console mounts, under the
chrome provider — 500 lines of save/publish/preview/schedule handling around fifteen dynamic
widgets, of which the chrome is a header, a save bar and one panel. SH900's rule, applied.

**§LV.4 predicted "the adapter needs a prop it does not have", and it was sharper than a prop.**
In bento the morph is **one** component: `BentoFormHero` renders the controls in the hero and
fades a fixed bar in as the hero scrolls away, and the authoring contract forbids composing those
halves by hand — "two halves that drift". In console chrome the same thing is **two** components
with no relationship: `PageHeader` and `StickySaveBar`. So `PageHero` absorbed the save
bar and now spans a 1:2 mapping.

It decides on `onCancel`/`saveBadges`, never on `actions` alone — the content browser passes two
navigation buttons and must not get a save bar.

The post-type chooser is a **copy**, not an adapter: its `Card`s are clickable choices, whose
bento counterpart is `BentoTile`. An adapter that turned a panel into a tile would be a different
component, not a chrome switch.

### SH897's morph check was looking for markup that does not exist

It searched for `[data-bento-save-bar], .bento-save-bar`. **Neither is rendered by anything.**
`BentoScrollSaveBar` emits `.bento-save-bar-spacer` and wraps the fixed bar in `.bento-fade-in`.
So the check could not have found a morph on any page, and its report — "no surface carries one
yet" — was true by construction and would have stayed true after one arrived.

It took a surface actually having a morph for that to become visible. **Give a check its subject
as early as you can.** Fixed against the package's real markup, and it now distinguishes the two
answers it exists to tell apart: `morph: ok` on the post editor, `no surface carries one` on the
home. Verified: `opacity: 0, pointer-events: none` at the top, `opacity: 1, pointer-events: auto`
after scrolling.

A second wrong answer sat beside it — `checkBentoSurface` returned a `morph` boolean from the
same invented selectors that nothing read. Removed rather than fixed: a second, wrong answer
beside the right one is worse than none.

Census after this task: **43 pages, Card 33, PageHeader 36, StickySaveBar 1** — and six pages
now empty, four of them empty *by migration* rather than by never having had chrome.

## A header-less section is a console card in both eras (SH902)

The post-type slice — list, new, edit — is three pages, and the migration was meant to be the
routine one: `PageHero` on all three, the edit page passing `saveTitle`/`onCancel`/
`loading`/`saveDisabled`, three routes mounted under `SectionCardChromeProvider chrome="bento"`.
That part was routine. What it found was not.

### `AdaptiveSectionCard` re-skins only when it has a header

The package's own branch is `chrome === "bento" && header`, and the fallback is deliberate: a
section built some other way falls through to the console card rather than losing its content
silently (SH892). Correct for the package, and wrong for four of Shio's panels.

The content browser's stats strip, its toolbar and its listing, and the post-type list, are
**panels, not sections**. They carry a count, a breadcrumb, a view toggle, a list of rows — and
the hero directly above already names the page, so a header on any of them would be noise. All
four were written with `AdaptiveSectionCard` and no header, and **all four rendered console cards
under the bento shell**. Three of them were introduced by SH900, in one commit, by the author who
had documented the trap two tasks earlier.

It was invisible in review, in `tsc` and in every source scan: the page imports the adapter, so
the census reads it as migrated, and the era classifier is right to say so. What found it was
`checkBentoSurface` reading `backdrop-filter` off computed style — `frost: null` on a page that
looked finished.

[`panel.tsx`](../../shio-react/src/components/panel.tsx) is the fix: frosted
container, no header, one implementation instead of four hand-rolled `bento-glass rounded-2xl`
call sites that drift the moment two of them differ. **SH923** asks the package for the component
so this can be deleted; the classes are copied from `BentoSaveBar` so the two match today.

`chrome-adapter.test.mjs` now fails any page that mounts a header-less `AdaptiveSectionCard`, and
`bento-browser.test.mjs`'s SH900 assertion was re-pointed from counting adapters to counting
panels. That correction is worth more than the count: the old assertion passed on all three
browser panels while the bento route rendered three console cards. **It counted the seam and
never asked whether the seam did anything.**

### The name-key question the section raised, answered by looking

§LV.5 flagged it rather than assuming: post types are the one name-keyed surface, so the identity
in the hero is a name and not an id, and an inline-edit hero that let it be typed over would be a
rename with no confirmation — P7 exactly. It is not. The hero edits the **display title**; `name`
stays a read-only input with its own Rename action, which is SH117/SH163 already decided.
Confirmed in a browser: `apiNameReadOnly: {value: "Page", readOnly: true}`, Rename present, four
fields, and `morphAtTop: "0"` → `morphAfter: "1"`.

### The first console probe to reach zero

`StickySaveBar` now matches **no page**, and the census's own vacuity rule prescribed what to do:
*"Either every page carrying it was migrated — then delete the probe in the same diff — or its
import path was renamed."* It was the first, so the probe is gone. Not lost coverage —
`chrome-adapter.test.mjs` asserts that no page mounts `StickySaveBar` directly, which is the same
rule from the other side and a stricter one.

Census after this task: **43 pages, Card 31, PageHeader 33, Sidebar 1, nine empty.**

`console-check` gained three probes rather than one — list, new and edit. The two that carry no
save bar are the two that caught the header-less defect, which is the argument for probing a
slice instead of its most interesting page. 25/25 green.

## Three surfaces where empty is normal, and a grep that found thirteen (SH903)

Search, the trash and the static-file list. §LV.6's argument was that all three routinely render
nothing and each says so in its own way, so the shared empty state is what they are for. Correct,
and short by a factor of four.

### The grep is the deliverable, and it lands first

SH322's rule applied literally: [`empty-state.test.mjs`](../../shio-react/test/empty-state.test.mjs)
was written before any of the three fixes, and it found the hand-rolled block in **thirteen** files
where the section named two. Not because anyone was careless — each was written months apart by
someone who could not see the other two, each was right in isolation, and **nothing objected**. The
fourteenth would have gone in the same way.

What it matches is the *pairing* — a centred `py-16` void plus the muted `h-8 w-8` icon chip —
rather than either half, which is what keeps it off the load spinner and the centred dialogs.

Three are fixed here and the post-type editor's is fixed too, because SH902 had already made that
page a bento surface and a hand-rolled console void under the frosted shell is that task's defect
exactly. The other **nine belong to SH904–SH908**, which rewrite those pages wholesale; converting
their empty states now would touch each file twice and span four tasks in one diff. They are held
in `STILL_HAND_ROLLED`, **each row naming the task that deletes it** — and the reader fails in both
directions, so a task that migrates a page and leaves its row behind is caught too. A debt list
nothing shortens is a list that only grows.

### `BentoListPage` is not what these pages are

§LV.6 asked for a mosaic. The package has one — `BentoListPage` — and it requires a `newRoute` and
renders a dashed create-tile, because it is built for entity lists. **None of these three creates
anything**: search finds, the trash restores, the file list uploads from a picker rather than
navigating to a form. Passing dummy props to satisfy a shell whose central affordance does not
apply is how a page ends up with a button that goes nowhere. All three took the adapter shape
instead, and the one surface that genuinely *is* a mosaic — the file grid — got the tile classes
directly.

### Two failures a source scan could not have produced

The live check found both, and both were mine:

- **`bento-files` had no frosted surface.** `MediaCard` was never a `Card` — it is a hand-rolled
  `rounded-lg border bg-background` tile, so a grep for console chrome does not find it and the
  census counted this page as carrying *one* `Card` while it renders a grid of forty. It now
  carries `bento-tile bento-glass` in bento chrome: the same classes `BentoTile` puts on its own
  root, not a copy of the look.
- **`bento-search` had no frosted surface either**, and that one is a real gap rather than a missed
  conversion. Search has **three** states and the third is the one it opens in: before a query has
  been run it rendered *nothing at all* below the form. An empty result is normal here — and so is
  not having asked yet, and the two want different words. "No results for that" on a page nobody
  has searched reports a failure that did not happen. `content.searchPromptTitle`/`Hint`, both
  locales.

Neither is visible in `tsc`, in review, or in any reader that reads imports. The probe landed on
the unsearched state only because it opens the route with no query — **give a check its subject and
it finds what you did not think to look for**.

Census after this task: **43 pages, Card 28, PageHeader 30, Sidebar 1, twelve empty.**

## The identity slice, where the design section was wrong twice (SH904)

§LV.7 called users, groups and roles "the clearest case in the product for the entity shell",
because "each of these entities has a title, a description and an enabled state". Two of those
three claims do not survive opening the models, and both matter.

**There is no `enabled`.** `ShGroup` and `ShRole` are `{id, name, description}` and nothing else,
so `hasStatus` stays off. A status pill driven by a column that is not in the model would have
rendered "Idle" against every group in the instance and meant nothing.

**A user has neither a title nor a description.** `ShUser` is
`{username, firstName, lastName, email, password?}`: its identity is a **login key** and its name
is **two columns**. `BentoEntityShell`'s hero inline-edits a single `title` and commits on blur, so
mapping `title` to `username` renames someone's login by tabbing away from it — P7 with no
confirmation and no undo — and mapping it to the full name needs a whitespace split that is lossy
for half the names in the world.

So the slice divides where the data divides: **groups and roles take the shell** (one shared
`IdentityEntityPage` and one shared `IdentityListPage`, because the two entities are the same
shape and the console's two copies had already drifted in their breadcrumbs), and for users the
**list takes the mosaic** while the **editor stays an adapter page**. The post-type editor is the
precedent for the read-only key (SH902/SH117); a user has no equivalent Rename affordance, so the
hero does not get the field at all.

### Three defects, and the one that mattered was invisible

- **A static `admin/groups/new` route beside `:groupId`** matches first and carries *no param*, so
  `useParams().groupId` is undefined, `isNew` is false, the load never runs and the page renders
  `null`. It reads as a route-ordering problem and is the opposite one. `new` is a **value** of
  `:id`, which is the sentinel the console already used.
- **`autosaveOnly` cuts both ways.** It hides Save on an existing entity — correct, the hero
  autosaves — and deliberately *keeps* it on a new one, because there Save is what creates the
  entity. A pure-identity entity has an empty form body, so the create branch has to render a real
  `<form id={formId}>` or the only button on the page is inert. That is SH899 again, in the one
  mode `autosaveOnly` does not cover.
- **The create call sent `{id: "", …}`** — the field is required by the type, and the empty string
  is the honest value for "not assigned yet". `POST /v2/group` **stored it verbatim.** The group
  appears in `GET /v2/group` and in the console's list, and nothing can address it: `/v2/group/`
  does not route and every id-shaped path 404s. Read 200, delete 404, still listed afterwards; it
  had to be removed at the database.

The third is the one worth dwelling on. Every visible thing about the flow was right — the page
navigated to the list and the group was on it. It was found only by **creating a group and reading
the row back**, which is the difference between driving a flow and looking at it. The client half
is fixed here (`create` takes `Omit<IdentityEntity, "id">`); **SH924** carries the server half,
because any client can still do this and groups are unlikely to be the only entity that allows it.

### One correction to the check itself

`checkBentoSurface` asked for a non-empty `h1`, which was written against `BentoHero`. The entity
shell does not render one: its title is a `BentoInlineEdit`, which is an `<input>` carrying the
title as its *value* with `headlineFallback` as the placeholder — and on a create page the title
is empty by definition. So **no entity-shell page could ever have passed**, and the first four
probed were the first four written. It now accepts either shape.

The frost rule needed the opposite treatment. A create page for an all-identity entity genuinely
has no panel and never will, so `noPanel` is declared **per probe, next to the route** rather than
exempted inside the check — the failure this rule exists to catch (SH902) is precisely "a page
that renders no frosted surface", and a global exemption would have retired it.

Census after this task: **43 pages, Card 27, PageHeader 29, Sidebar 1, thirteen empty.**

## Eleven pages, split where the data splits (SH905)

API tokens, auth providers, exchange providers, site webhooks, tenants and email configuration:
five list-and-form pairs and a settings page. §LV.8 called them the most repetitive surface in the
console and it was right — the five lists differ only in **which two columns sit between the name
and the status**.

So the slice divides by what each half actually is:

- **The five lists become configuration** on one shared
  [`AdminListPage`](../../shio-react/src/app/bento/admin/admin-list.page.tsx) — roughly forty lines
  each of `query`, `tileOf` and labels. That component started as SH904's `IdentityListPage`, fixed
  to `{id, name, description}` because that is all a group is; generalising it rather than writing
  a second one is the whole point of the slice. **Two nearly-identical list components is the state
  this task exists to leave, not to arrive at.** All eight admin lists are that one component now,
  which is why a single assertion covers all of them.
- **The six editors stay their console components** under `SectionCardChromeProvider`. Their
  identity is name + description + enabled, which the entity shell handles well — but their
  *bodies* are the substance: a vendor property map, a scope and site picker, an event set, a
  provisioning flow. The shell's value is lifting identity out of a form, and on a 300-line token
  form that is three fields of twelve. `PageHero` gives the same morph at a fraction of
  the risk (SH900's rule).

Every card in all six had a real `CardHeader`, so this slice took `AdaptiveSectionCard` where
SH902's header-less panels needed `Panel` — and a lint now holds that, because a sweep this
size is exactly where SH902's defect gets reintroduced eleven times.

### The page that needed something — and needed the opposite

§LV.8 said to "notice when one of them needs something the shell does not have instead of working
around it eleven times". One did stand out, and it turned out to have **hand-rolled what the
adapter already gives**: email configuration carried a `sticky top-0` div repeating the page title
with a Save beside it. It duplicated the heading, and it was sticky *without* a morph — over the
content from the first pixel of scroll, rather than fading in as the hero left, which is the one
behaviour the save-bar design is about.

Its conversion turned up the package rule worth writing down: **`actions` replaces the hero's own
Save/Cancel** (`children: actions ?? <submit + cancel>`). That matters because the hero's default
Save is a bare `type="submit"` and this page has no form — taking the default would have been
SH899's dead button one more time. A page whose save is an `onClick` passes it as `actions`; a page
with a real form lets the default submit it.

### A route param that does not match is a blank page and nothing else

`<Route path="admin/auth-providers/:providerId" element={<AuthProviderPage />} />` against a
component reading `const { authProviderId } = useParams()`. React Router matches; the component
reads `undefined`; `isNew` stays false, the load never runs, the page renders `null`. **No error,
no failed request, and `tsc` is silent** — `useParams()` returns
`Record<string, string | undefined>`, so every param name type-checks.

Two of the five had it. SH904 hit the same shape from the other end — a static `/new` route beside
a `:id` one, matching first and carrying no param at all. Same symptom, same silence, twice in two
tasks, which is what earns an instrument rather than a fix:
[`bento-admin-slice.test.mjs`](../../shio-react/test/bento-admin-slice.test.mjs) pairs every
parameterised bento route with the params its component actually reads. Verified by reintroducing
the defect and watching it name the route.

Its own vacuity check is worth copying: the first draft's route pattern only matched the one-line
`<Route>` form and found **8 of 13**, reporting a clean tree while silently skipping five. It now
asserts the parsed count equals a raw count of `path="…:param"` in the same file, so a pattern that
stops matching fails instead of passing.

Census after this task: **43 pages, Card 21, PageHeader 23, Sidebar 1, nineteen empty** — and
44/44 live probes, every one of the eleven opened in a browser.

## Three bespoke pages, and a catalogue nobody could reach by keyboard (SH906)

The review queue, the activity log and the marketplace — three layouts with little in common.
§LV.9 predicted the shape of the problem exactly: two of them are **read-mostly surfaces**, "which
is the page shape the package covers least well — a hero over a table is not a mosaic", and it said
to let them have a plain section rather than tiles.

They did. The review queue is a list of expandable agent sessions and the activity log is an audit
table; both take `PageHero` + `Panel` and stay exactly the shape they were. An
audit log is read down a column, and turning it into a mosaic would trade a readable column for a
grid of cards saying the same thing worse. The marketplace is the one that genuinely is a mosaic,
so its grid stayed a grid.

**The review queue was the point of the slice** — it is the curator's half of the agent-native design,
where a curator goes when they do not trust what an agent did, and §LV.9's whole argument is that
it should not be the last part of the console that looks old. It is not.

### The defect: a catalogue reachable only by mouse

Every marketplace tile was `<Card className="cursor-pointer" onClick={() => open(item)}>` — a div
with a handler. **Not focusable, not announced, no key handler.** The entire catalogue, including
every install action behind it, was unreachable without a pointer.

This is SH922's sweep from the other side and worth stating as a pair, because the two are found by
opposite means. SH922 looked for **interactive things nested inside interactive things**, which a
DOM walk finds. This is a thing that *looks* interactive and is not one — no nesting, nothing to
walk, and it renders identically to a working control. The tile is a real `<button>` now, inside
the panel rather than instead of it.

The assertion holds the property rather than the fix: no `Panel` in that grid may carry
`onClick`. Wrapping it in a button, making it a link, or giving the panel real button semantics all
pass; a div with a handler does not.

### The heading every one of these pages said twice

All three rendered `<h2>{t("x.title")}</h2>` with the description under it, directly below a
`PageHeader` already showing the title — because the console header has no subtitle slot, so the
description had nowhere else to live. The bento hero takes both, so the pair had to go or the page
would name itself twice with nothing between. That is the fourth and fifth time this exact
duplication has appeared in Block AR (SH898 twice, SH899 twice), which is why it is now asserted
rather than remembered.

Census after this task: **43 pages, Card 18, PageHeader 20, Sidebar 1, twenty-two empty** — and
47/47 live probes.

## A surface that owns the viewport has no chrome to migrate (SH907)

§LV.10 put this slice late on purpose: "every other slice in this block risks appearance; this one
risks behaviour, because the bridge depends on what surrounds the frame — the sizing, the scroll
container, the overlay stacking — and the bento shell changes all three."

It was right, and the measurement is worth keeping. `visual-editor.page.tsx` is
`flex flex-col h-screen`: it owns the viewport, draws its own toolbar, and splits the rest between
a preview iframe and a properties panel. Mounted inside the bento shell and read in a browser at
1600×1000, against the same page mounted outside it:

| | iframe top | iframe bottom | fits in 1000px | doc overflow |
|---|---|---|---|---|
| inside the shell | 137 | 1048 | **no — 48px below the fold** | 64px |
| outside it | 73 | 984 | yes | none |

One viewport *plus* the shell's 64px `sticky top-0` header, and two sticky bars stacked at the top
of the page.

### The fix is a route, not a container

`calc(100vh - 4rem)` would have worked and would have been wrong: it makes the editor's height a
fact about the shell — the coupling §LV.10 is warning about, written into CSS where nothing reads
it, and stale the next time the header changes. **A surface that owns the viewport has no chrome to
migrate.** The editor renders the same in both eras because it renders neither era's chrome, so it
is mounted as a sibling of the shell (beside `/bento-vocabulary`, which is there for a related
reason) and the bridge's environment is byte-identical to the one it has always had.

Outside the shell, the bento route is measurably *better* than the console one: `overflows: false`
against the console's 58px, and the properties panel's bottom is at 1000 (visible) where the
console route puts it at 1008 (cut off). That is a small pre-existing console defect, and the
console tree goes with SH909/SH910.

### What this task did not prove

§LV.10 also says "a digest is least sufficient and a real click-through is required". **The
click-through did not run.** `universal-editor-drive` skips on a dev box — the console is `:5173`
and the renderer answers as `:2710`, so the bridge posts to an origin this window is not, and the
handshake cannot complete across the Vite proxy. That is a real gap in this task's evidence rather
than a formality, and the check reports it as a skip on every run (SH238/SH659 make it a *failure*
under `--strict`, which is what CI passes).

What did run: both routes render with the same iframe `src` (`/preview/<site>/?editor=true`), the
same edit-mode switch, the same properties panel, and no page errors — and the geometry above,
which is the half §LV.10 was actually worried about.

Census after this task: **43 pages, Card 17, PageHeader 19, Sidebar 1, twenty-three empty** — and
49/49 live probes.

## The last two, and the crash that had no lint (SH908)

The GraphQL explorer and the not-found page. §LV.11 put them together because both are "mostly
something the console does not control", and the second one matters more than its content
suggests: during a cutover it is where a stale bookmark or a redirect that has not landed puts
somebody who did not choose to be there.

The explorer is the adapter case (SH900's rule): `PageHero`, the token toolbar as an
`Panel`, the no-tokens void as the shared `EmptyState` — the last row in SH903's
`STILL_HAND_ROLLED`, now empty. Its body is GraphiQL, which is nobody's chrome.

**Its bento route is the one Block AR route not declared in `App.tsx`.** The page is `lazy()` so
Monaco stays out of the main bundle; naming the component in `App.tsx` would have imported it
eagerly and undone that, with nothing in `tsc`, the census or any probe to say so. `BentoGraphQLRoute`
lives beside the console one in `graphql.routes.tsx`, and the build still emits `graphql.page`
(3 kB) and `vendor-graphiql` (4.8 MB) as separate chunks.

### Console chrome outside the console shell is a crash, not a style mistake

`PageHeader` renders `SidebarTrigger` → `useSidebar()`, which **throws** with no `SidebarProvider`
above it — and only `console.root.page.tsx` mounts one. So a console header anywhere in the bento
tree, or at `App.tsx`'s bare `*`, blanks the application behind the error boundary.

That is why the not-found page has rendered no chrome since SH180, and **nothing recorded it**. The
census called it "console-era by absence", which reads as an accident of size rather than a
constraint. So the obvious migration — give it an `PageHero` like the other thirteen
surfaces — is a white screen on the one route that matters most.

[`outside-shell-chrome.test.mjs`](../../shio-react/test/outside-shell-chrome.test.mjs) is the
instrument, and it asks the question of **every** component `App.tsx` mounts outside the console
shell — 44 of them — rather than of the one that was found. It caught a second, transitive form in
the same run: at the time, `AdaptivePageHeader`'s console branch **was** `PageHeader`, so a page
composing the adapter with no `SectionCardChromeProvider` above it carried the same crash one file
away. (SH910 deleted that branch and SH932 re-pointed the probe — see below.) **It
found one on its first run**: `bento/content/post-type-select.page.tsx`, shipped by SH901, rendering
`PageHero` at a bento route with no provider — `/bento/content/folder/:id/post-type-select`
was a blank page. It renders `BentoHero` now; a bento-only page has no second chrome to adapt to.

### Six ways the Monaco workers do not get fixed (SH927)

Under the Vite dev server the explorer's workers are **created** and then die evaluating their own
module graph: `monaco-graphql` reaches CommonJS packages that Vite serves raw to a worker, where the
app graph gets the optimizer's interop. A module worker that fails to evaluate raises an event
carrying nothing, so `new Worker` reports no message — importing the same URL from the page is what
prints the cause. The page is fine: Monaco falls back to the main thread and works, slowly.

Every one of these was measured and refuted, and they are written into `console-check.mjs` so the
next reader does not spend an afternoon re-running them:

| attempt | result |
|---|---|
| exclude the three `?worker` entries from `optimizeDeps` | imports resolve to real `/@fs/` paths, warning unchanged |
| suspect module workers | one built by hand in the page starts fine |
| `optimizeDeps.include` the CJS package | no chunk — `graphiql` already swallowed it |
| `optimizeDeps.needsInterop` | the import is rewritten no differently |
| alias it to a local ESM shim | **works**, and reveals the next CJS package — a chain, not a package |
| frame the GraphiQL Spring serves (Turing's design) | that page loads twenty resources from `esm.sh` with **no local fallback** |

The last one is the finding worth keeping: Shio bundles 4.6 MB of `vendor-graphiql` **so the console
does not need a CDN at runtime**. Turing can frame Spring's page because it accepts that dependency;
Shio would be trading an offline-capable console for a lighter bundle.

So the exemption is the earned answer, not a shrug — dev server only, explorer routes only, still a
failure against the jar, and it expires by failing the day the workers start.

### The not-found page renders in whatever chrome the path implies

Three mount points, one component: inside the console shell, inside the bento shell (this task's
catch-all, declared last so it only catches what every real bento route declined), and the bare `*`
for a path under neither. It reads `useSectionChrome()` and renders the hero only under bento —
where the Back-to-Home also leads to `/bento` rather than into the console.

### The height a third-party editor needs, measured rather than assumed

GraphiQL sizes itself to its container, so the container must name a height, and the only height
available is the viewport minus whatever chrome sits above it. SH907 says not to leave that as a
CSS expression nobody reads, so `console-check` measures it on every run, in both chromes, at
1600×1000:

| | editor top | editor bottom | viewport | below fold | doc overflow |
|---|---|---|---|---|---|
| `/console/graphql` | 198 | 935 | 1000 | 0 | 0 |
| `/bento/graphql` | 282 | 984 | 1000 | 0 | 0 |

`calc(100vh - 4rem)` holds in both eras for two *different* reasons — the bento shell's `sticky
top-0` header above it, the console's `AppFooter` below — which is exactly why the number is read
off a rendered page rather than trusted to a comment.

### Two findings the probe produced by having a subject

- **The explorer had none.** It is token-only (SH4), so on an instance with no enabled CDA token it
  renders its empty state and never mounts GraphiQL. `provision.mjs` creates a READ token now, so
  CI measures the editor; without one the check reports a **skip** with its reason, and `--strict`
  turns that into a failure the way SH238/SH659 did for the editor drive.
- **`/bento` 404s on a packaged instance.** `ShStaticResourceConfiguration.REACT_CONTEXTS` is
  `{"/login", "/setup", "/console"}`, so the jar forwards none of the bento tree to the SPA shell —
  measured: `/console` 200, `/bento` 404, same instance, same second. Every live probe in this block
  ran against the Vite dev server, which serves `index.html` for any path. **SH926** carries it, and
  it is the one that matters: a surface is not migrated until the thing that serves it in production
  serves it. The Monaco worker failure the same run turned up is dev-server-only — the jar's build
  is clean — and is **SH927**.

Census after this task: **43 pages, Card 17, PageHeader 18, Sidebar 1, twenty-four empty** — and
50/52 live probes on the dev server, the two failures being SH927 rather than either page.

## The whole block was proved on a server nobody ships (SH926)

`ShStaticResourceConfiguration.REACT_CONTEXTS` was `{"/login", "/setup", "/console"}`, and those
three strings are the whole of what a packaged instance forwards to the React shell. The bento tree
is a **sibling** of `/console` (SH888), so nothing matched it. Measured on a running jar, in the
same second:

| path | jar | dev server |
|---|---|---|
| `/console` | 200 | 200 |
| `/console/content` | 200 | 200 |
| `/bento` | **404** | 200 |
| `/bento/content` | **404** | 200 |
| `/bento-vocabulary` | **404** | 200 |

Fourteen migrated surfaces, unreachable by URL on the only deployment anybody runs — a typed
address, a bookmark, a link in an e-mail, a redirect. SH908's bento catch-all is the sharpest case:
a route whose whole purpose is to catch a stale link during a cutover, 404ing on the one deployment
where a cutover happens.

**Every live probe in Block AR ran against the Vite dev server**, which serves `index.html` for any
path that is not a file. That is what a dev server is for, and it is why "the surface renders" and
"the surface is reachable" are different claims — the block only ever checked the first, fourteen
times, and reported `n/n probes ok` each time. The console-check workflow does run against the jar,
which means the answer to "why did CI not say so" is that it did.

### Two readers, because the two sides fail differently

- [`ShSpaContextIsServedTest`](../../shio-app/src/test/java/com/viglet/shio/spring/ShSpaContextIsServedTest.java)
  issues the request rather than reading the array back: an exact context must forward to
  `/index.html`, a client-side route under it must answer 200 with the shell, and a path under no
  context must still 404 — the last one is not decoration, because a fallback that became global
  would make the other two vacuous.
- [`spa-contexts.test.mjs`](../../shio-react/test/spa-contexts.test.mjs) asks it from the side that
  *changes*: every top-level route tree `App.tsx` mounts is a declared context. A new tree is added
  in the React app by somebody with no reason to be thinking about a Java file two modules away,
  which is exactly how SH888 added one.

`/bento` and `/bento-vocabulary` also join the `permitAll` block beside `/console/**`: what is
public there is the shell, not anything it reads, and every API it calls is governed by the rules
under it. `ShAllowListIsGroundedTest` requires each new entry to be declared as non-controller with
its reason, which is where those two say so.

### Two things the reader got wrong before it got them right

Both were silent, and both are the reason it is written the way it is.

- **A lazy `<Route[\s\S]*?(\/>|>)`** reads `<Route path={ROUTES.BENTO} element={<BentoRootPage />}>`
  as self-closing — the match ends at the `/>` of the element prop — so every child of the tree read
  as top-level and the reader demanded an SPA context for `/content`. `findElements` failed the
  *other* way: `bodyOf` counted every `<Route` before the next `</Route>` as a nested open,
  self-closing children included, so a parent with thirty of them never balanced and was dropped
  entirely. `endOfOpeningTag` is exported now and the depth is counted over tags in source order.
  **SH928 fixed `bodyOf` itself**, so that second failure is history rather than a live constraint
  — but this reader keeps its own walk, because what it needs is nesting and not bodies.
- **`ROUTES.CONSOLE` is not a literal.** It is `scoped(consolePath)`, and `consolePath` is
  `import.meta.env.VITE_CONSOLE_CONTEXT_PATH` — the console's context path is a build-time setting,
  which is a real coupling to the Java array. The first draft skipped what it could not parse and
  printed four contexts with `/console` silently missing: a clean run over the one route every
  curator uses. It resolves `.env` now and throws if it cannot, and the vacuity test asserts the
  console tree is among the routes found.

## The scanner six readers trust, and nobody scanned (SH928)

`test/support/jsx-elements.mjs` is how `chrome-adapter`, `bento-a11y`, `bento-bespoke-slice`,
`bento-browser`, `bento-site-slice` and `spa-contexts` all ask what a file renders. It had no test
of its own until now, and it was wrong: `bodyOf` counted every `<Tag` before the next `</Tag>` as a
nested open. A self-closing child carries no closer, so a container holding thirty of them was
short thirty and never balanced — and a container that never balances is **dropped**.

Measured on the smallest case that shows it and on the file that motivated the task:

| | raw `<Tag` | returned | containers |
|---|---|---|---|
| `<P attr><P /><P /></P>` | 3 | **2** — the two children | 0 |
| `App.tsx`, tag `Route` | 50 | **48** | **0** of 2 |

The two it lost were the console tree and the bento tree: the only two elements in that file a
caller would ask about a route *tree* through.

### The direction is why it was a task and not a note

A scanner that drops an element **under-reports**, which is the failure this file was written
against in the first place — SH893's regex, fooled by the `>` in `=>`, reported 3 unnamed icon
buttons where there were 34. One level up, same shape: a lint asking "does any of these carry
`onClick`" answers *no* about a container it never saw, and a reviewer reads that as coverage.

None of the six readers was giving a wrong answer when this was found, because none of their tags
happened to nest self-closed inside itself. That is a property of a day's markup, not of the tool,
and it is the kind of luck that expires with nobody noticing.

### The fix, and where the assertion is pinned

`bodyOf` resolves every candidate open through `endOfOpeningTag` — the same scanner the caller
already used, so there is no second implementation of "where does this opening tag end" to get
wrong about `=>` — and only a non-self-closing one increments the depth. An opening tag that never
ends returns `null`, the way the rest of the file declines to guess.

`test/jsx-elements.test.mjs` asserts it on **written-out fixtures, not on `App.tsx`**. `App.tsx`
was the right place to find the defect and the wrong place to pin it: SH909 and SH910 are
rewriting its route trees, so an assertion reading that file would go red on a change with nothing
to do with this scanner. The shape is what matters, so the shape is what is written down — a
parent and its two children, a miniature route tree, real nesting, the arrow-in-a-prop case, and
the unterminated tag that must still yield nothing.

SH926's own reader keeps its hand-rolled depth walk. It needs nesting rather than bodies, which is
what `endOfOpeningTag` was exported for; the workaround comment that described the bug as live has
been corrected above.

## The choice, made once (SH909)

Fourteen surfaces migrated and the console still mounted **two route trees, two shells and two sets
of chrome**. §LV.12's argument is not about appearance: "a reversal nobody claims becomes a
permanent second product", and the legacy AngularJS console has been in exactly that state, beside
its migrated pages, long enough for it to look normal.

So: `/bento` is the console. `ROUTES.CONSOLE` is `/bento`, and one line does it —

```ts
const CONSOLE_BASE = "/bento";
```

— because every `navigate()`, every `to=`, every `urlBase` and the whole nav declaration are built
from those constants. The 24 call sites that compose a path from `ROUTES.CONSOLE` moved without
being touched, which is what the constants were for.

### Redirect, don't drop

`/console/**` is in bookmarks, in e-mails, and in redirects this repository does not control, so
the old prefix is a route whose element is [`LegacyConsoleRedirect`](../../shio-react/src/app/legacy-console-redirect.tsx).
Two patterns — the bare one and `/:tenant/console/*`, which is what a multi-tenant install's
bookmarks carry — and `bentoPathFor` handles the two paths that are **not** a prefix swap:
`/console` and `/console/home` are the same page under bento, and it is the shell's *index* rather
than a `/home` route (SH898). Search and hash are carried across: `?q=` on search is the one place
where dropping a query looks like a page that simply found nothing.

`replace` rather than push, or Back from a bento page returns to the console path, which redirects
forward again — a trap the reader cannot escape with the button they reached for.

Measured in a browser, every legacy shape:

| opened | landed on |
|---|---|
| `/console` | `/bento` |
| `/console/home` | `/bento` |
| `/console/content` | `/bento/content` |
| `/console/admin/review` | `/bento/admin/review` |
| `/acme/console/content` | `/bento/content` |
| `/console/content/post/<id>` | `/bento/content/post/<id>` |
| `/bento/admin` | `/bento/admin/users` |

57/59 probes, the two failures being SH927's dev-server Monaco workers on the GraphQL page — the
same two that fail on `main` and pass against the jar.

### The tenant prefix is dropped, and that is smaller than it looks

The console path was tenant-scoped (`/acme/console/...`); bento is not. **That prefix was never how
a tenant was resolved**: `ShVigletTenantResolver` reads the session (set by
`POST /tenant/{name}/switch`), a CDA token's own tenant, or an OIDC `workspace_id` claim, and never
the URL. It kept generated links inside whatever prefix you arrived with, and nothing else. What is
genuinely lost is sharing a link that names its tenant, which is **SH931**.

### The adapters stopped adapting, and stopped being called Adaptive (SH932)

`AdaptivePageHeader`, `AdaptivePanel` and `AdaptiveEmptyState` are `PageHero`, `Panel` and
`EmptyState`. The name was the defect: a wrapper called *Adaptive* tells the next reader there is a
chrome to choose, and the cheapest way to honour that reading is to write the branch back — which is
how a retired era returns one page at a time.

**The premise was right about two of the three, and SH932 measured the third rather than inheriting
it.** `PageHero` has no chrome branch at all: SH910 deleted it, and what is left maps this product's
header props onto `BentoHero` or `BentoFormHero` depending on whether the page wants a save bar.
`Panel`'s console branch was **dead** — every call site is under a `chrome="bento"` provider,
including the one that looked like an exception, since `not-found.page.tsx` composes it inside its
`if (bento)` arm — so the branch is gone and the file is a seam, not an adapter.

`EmptyState` still branches, and **not between eras**. It chooses between inside the shell and
outside it: `not-found.page.tsx` is mounted at `App.tsx`'s bare `*`, under no provider, so
`useSectionChrome()` answers with its `console` default. That route is permanent — a 404 for an
unknown path cannot live inside a shell that only covers known ones — so the branch is not
migration debt.

That correction reached an instrument. `outside-shell-chrome.test.mjs` carried a transitive probe
built on "`AdaptivePageHeader`'s console branch *is* `PageHeader`", which SH910 had made false, so
it was guarding a crash that could no longer happen — the exact rot that file's own SH910 note warns
about, one component along. It now holds a named `CHROME_BRANCHING` list and asserts each entry
still reads the hook, so the list cannot rot into names that branch on nothing. Proven by removing
the hook from `not-found.page.tsx` and watching it fail.

One collision worth knowing: `static-file.list.page.tsx` already had a local `EmptyState` deciding
*which message* an empty file list shows — filtered-out versus genuinely blank. It is
`FilesEmptyState` now, named for the surface rather than the shape.

### The frosted panel is the package's now (SH923)

`Panel` carried `bento-glass rounded-2xl border shadow-md` copied out of `BentoSaveBar`,
with a comment promising the two would be kept in step by hand. **VDS79 exports `BentoPanel`** and
2026.3.5 is the first release carrying it, so the copy is gone: nothing in this console decides what
a frosted panel looks like, and the next consumer that needs one asks the package instead of picking
its own radius. What is left in `components/panel.tsx` is the console fallback branch, and
**SH932** is the task that removes both it and the file's name.

Adding the export tripped `bento-vocabulary.test.mjs` (SH887), which is the reader that keeps the
proving page proving the whole layer rather than whatever it composed on the day it was written — a
new package export is either rendered on that page or declared `DEFERRED` with the task that owns
it. `BentoPanel` is rendered there, and it is proved by what it does *not* render as much as by what
it does.

### `?tenant=` is a claim about intent, and opening it changes nothing (SH931)

A bento URL names its tenant with `?tenant=<name>`. `bento-tenant-link.ts` owns the parameter and
both directions of it: `tenantFromSearch` reads the claim (a blank value is no claim), and
`linkWithTenant` stamps one, absolute and idempotent — copying twice is byte-identical, so an
already-stamped link is re-stamped rather than given two.

**Opening such a link never switches the session.** `BentoTenantLinkNotice`, mounted in the shell
above the `Outlet`, compares the claim with `GET /tenant/current` and renders nothing when they
agree — the silent case, which is everyone who produced the link. When they differ it names both
tenants and offers a switch the reader presses, or says plainly that they are not a member. The
rejected shape is the obvious one: a parameter that switched the session on open would make every
pasted URL a state change, carrying a curator out of the tenant they were working in and leaving a
half-written draft behind in it. That is P7, and the cost of getting it wrong is a lost edit rather
than a wrong page.

That rule is one line from being broken — move `switchTo` into the effect that already reads the
claim and nothing else notices — so `bento-tenant-link.test.mjs` slices the notice's `useEffect`
bodies and holds them empty of it, with a floor on how many it found so the scan cannot pass by
finding none.

The producer is a **Copy link** item inside `BentoTenantSwitcher`, which inherits both of that
control's gates: `multiTenant` and two memberships. So a curator who cannot switch cannot produce
an ambiguous paste either, and a single-tenant install — where every link means the only tenant
there is — gets neither the banner nor the affordance.

### What is waiting on a design-system release, and what notices when it arrives (SH937)

`shio-react/test/design-system-waits.test.mjs` holds the waits as a **named list** — id, what it
wants, a probe that answers "is the wait over", and the instruction to run when it is. Each goes red
on the day its wait ends, which is the shape SH919 shipped as a one-off and this generalises: the
next wait costs a line, not a test file.

**It has been through one release.** 2026.3.5 carried VDS78 and VDS79, and this reader failed three
times in one run — the version had moved, SH919's wait was over, SH923's wait was over — while
correctly leaving SH935 alone, which that release deliberately did not address. Both were shipped
off the back of it. Before it existed, SH919's line read ⏳ for a week after its fix was committed
upstream, because "has it landed" was three reads across two repositories somebody had to remember
to do.

Two probes per entry, and the coarse one is the important one. A precise probe reads the installed
package for the exact thing — SH935's is `fallbackRoute` disappearing from `BentoNavItem`. It can
under-report, and that is the dangerous direction: SH923 was waiting on a component **whose name
nobody had chosen**, so looking for `BentoPanel` would have missed a release calling it
`BentoSurface`. So `PINNED`
holds the installed version every entry was measured against and fails when it moves — every wait
here ends with *a release*, so that one cannot miss. Read the entries, ship what is over, then move
`PINNED`; moving it first is how a release that ended a wait passes through unnoticed.

A wait-watcher's own failure mode is that a read which has lost its file answers "not yet" for the
same reason a real wait does, so each entry also carries a `proof()` that its read reached something
real. Verified by running SH919's probe against the fixed gate in the design-system checkout: it
flips to "over", with the bare-root call the entry actually makes.

### The floor carries the page reference and the assistant, not only the subpaths (SH940)

`^2026.3.10` is the floor because it is the first release whose `files` carry what the console now
takes from the package besides imports: the two bins (`viglet-ds-page-reference`,
`viglet-ds-check-duplicates`), the docs payload the page reference copies (`BENTO-AUTHORING.md`,
`BENTO-BOUNDARY.md`, `docs/reference/`) and Block E's `VigletAssistant`, `VigletAvatar` and
`useAssistantNotifications`. 2026.3.5 had the subpaths and none of those. A bin is not in `exports`,
so `design-system-subpaths.test.mjs` reads the manifest and the files rather than resolving.

**`registerVigTranslations` comes from `@viglet/viglet-design-system/i18n`.** VDS117 took the i18n
runtime out of the root entry so a consumer that never asked for it does not resolve i18next; the
root import compiled under 2026.3.5 and is a `TS2305` under 2026.3.10. The unit suite did not see it
— only `compile:build` did — which is why a design-system bump is validated with the build, not with
`pnpm test` alone. It also makes `./i18n` Shio's seventh entry, which SH943's line now counts.

The bump read the waits list the way SH937 intends. SH935's wait was not over and never will be: it
shipped with the collapse abandoned, so its entry left the list instead of waiting on a release
nobody will cut. SH943 (VDS129's consumer-entries bin) and SH944 (the package's plugin manifest)
entered it, each with a probe and a proof, because they are now the lines waiting on a release.

### Two vendored directories, and nothing in them is hand-authored (SH941)

`.claude/skills/viglet-ds-pages/` and `docs/design/vds-*` are written by
`viglet-ds-page-reference`, a bin the design system ships. Run it from the repository root; it
writes thirteen surfaces — `SKILL.md`, `authoring.md`, `boundary.md`, nine `vds-*.dc.html`
artboards and `canvas.json` — and `--check` reports drift without writing. **Do not edit any of
them here.** The next run rewrites them, so a correction belongs in the package, where every
consumer gets it.

The catalogue answers whether a component exists; it cannot answer whether a page reads as the same
product, and that answer was in a repository nobody browses. Vendoring puts it where an agent
session and a person both trip over it — the skill loads before the first screen, an artboard opens
from the file tree.

Twelve of the thirteen are copies. `canvas.json` is a **merge**: the `vds-` pages, artboards and
annotations are re-derived and this repository's own are preserved, compared as parsed JSON so a
formatter is not drift. That merge has a failure a copy does not — the declaration and the files can
disagree, and a canvas naming an absent artboard opens a blank frame rather than reporting anything
— which is what `cli/test/page-reference.test.mjs` compares in both directions. It asserts presence
and coherence only.

**Whether the copies are still the package's is a second gate, and it lives inside `pnpm test`
(SH942).** `cli/test/page-reference-drift.test.mjs` spawns the bin's `--check --json` against this
repository and fails on any surface that is not `unchanged`, on an orphaned `vds-` artboard, and on
a `vds-version` stamp *newer* than the installed package — where refreshing would be a downgrade and
the bin refuses to write. Inside the JS gate rather than beside it, because a gate run through
`scripts/run-gate.mjs` keeps its log and stamps its outcome where the comparators read it, and
because a test reading a spawned process's own exit status cannot lose it the way a pipeline does.

Two properties of that gate are load-bearing, and changing either quietly defeats it. It is **broken
on purpose**: it plants each kind of drift the bin can see in a throwaway root and requires the check
to *name the file*, because SH897's browser check passed with `bento.css` deleted. And `canvas.json`
is compared as **parsed JSON, never as bytes** — reindenting it must stay green, or the first person
whose editor reformats it is the person who deletes the gate. Its control is a semantic edit to the
same file, which must still be caught.

### Which screens are worth drawing, and which are already decided (SH945)

`docs/design/` holds two kinds of artboard. The `vds-*` ones belong to the package (above). The
`shio-*` ones are this repository's own, on this repository's own `canvas.json` pages —
`shio-surfaces` and `shio-not-chosen` — which the bin preserves rather than rewrites.

**The 23 migrated console screens are deliberately not drawn.** Blocks AQ and AR settled them: one
route tree, the three shapes and the panel taken from the package, zero legacy chrome. An artboard
would re-open a decided question and cost days to confirm what the shell and `authoring.md` already
say. Drawing is for a layout that is still open *and* whose data model follows from the choice.

Five qualify, and each artboard states what it decides and names the direction it did not take:
revision history with a **field-level** diff (Block AZ — per-field is what SH989 needs, so a
revision stores a field map and not a rendered blob), the translation workspace (Block BH —
staleness is per field, so SH1058's digest is too), release bundles (Block BA — a release is
persisted and addressable, which is what gives SH994 a rollback anchor and SH998 a waiver somewhere
to live), the agent-supervision inbox (Block BB — the verdict and its reason sit on the *item*, the
intent on the *run*), and the asset browser (Block BE — every filter including "unused" is
server-side and paged, which is a claim on SH1033's index).

One rejected direction is kept **drawn** rather than described: `shio-release-tray.dc.html`, a
release as a session-scoped tray. It reads perfectly well, and everything wrong with it is invisible
on the screen — which is the argument for drawing it instead of arguing about it.

`cli/test/design-canvas.test.mjs` names the five, checks the canvas and the files agree both ways,
and — the one that matters — runs a real refresh against a copy and asserts this repository's own
pages, artboards and annotations come back unchanged. A release that stopped preserving them would
delete every drawing here, and the only sign would be a canvas that opens with fewer pages.

### The admin hub nobody had migrated

`ADMIN_ROOT` is the `urlBase` of eleven admin surfaces, so it is where their hero back-links point —
and no bento route claimed it. Under the console it was `admin.root.page.tsx`: a bare `Outlet` with
an index redirect to the user list. There is no hub page to migrate, so the bento route is that
redirect, and the probe above is what would have caught eleven dead back-links.

### What the switch broke in the readers, and why that is the interesting part

Three checks failed on their own premises, which is the honest way for a check to notice its world
moved:

- **`bento-shell.test.mjs` asked whether the bento route was nested inside the console one.** There
  is no console route, so it failed with "App.tsx no longer opens a route at ROUTES.CONSOLE". The
  rule it was protecting survives and is now stronger: *nothing in the app mounts sidebar chrome
  around the bento tree* — nesting was one way to reach that state, a wrapper above `<Routes>` is
  another, and the old form could not see the second.
- **`outside-shell-chrome.test.mjs` (SH908) required the console shell block to exist.** Now
  everything is outside a shell, so the rule applies to the whole application instead of part of
  it — 45 components rather than 44.
- **`spa-contexts.test.mjs` (SH926) lost `/console`**, because the legacy patterns are mounted
  through a `.map()` over a constant and no literal reader resolves `path={pattern}`. It reads the
  constant now. The failure to notice would have been the bad one: `/console` dropping out of the
  needed set is exactly the shape of "the redirect is unreachable on a packaged instance".

And one trap worth naming: **`stripComments` eats a route wildcard.** `` `${consolePath}/*` `` is
character-for-character a block-comment opener, so the stripped copy of `routes.const.ts` loses
everything from there to the next `*/` — the reader saw one backtick where there are four, and no
`/console` at all. The file's own doc warns that "block comments inside string literals would be
stripped too"; this is the first reader in the tree to be pointed at a string that is one. Patterns
are read from the raw source; prose-adjacent scans keep the stripped one.

## Retire what nothing renders (SH910)

**40 files deleted, and the census reads 43 → 23 pages with zero console chrome.** That second
number is the whole of Blocks AQ and AR stated as one assertion: `console-chrome-census.test.mjs`
opened at `Card 42, PageHeader 39, Sidebar 1` and every value in its map is now an empty list.

### The deletion set was computed, not remembered

A reachability walk from `main.tsx` — resolving `@/`, relative and barrel imports to a fixpoint —
is what produced the list, run again after each wave until only `vite-env.d.ts` remained (an
ambient declaration, never imported by design). Three waves, because deleting a file makes the
next one unreachable:

| wave | what it freed |
|---|---|
| the shell, the route files, 19 superseded pages | `page-header.tsx` had no importer left but the adapter |
| `page-header.tsx` | `header-user-menu`, `tenant-switcher`, `ui/sidebar`, `ui/breadcrumb`, `lib/breadcrumb-key` |
| those | `ui/avatar`, `ui/sheet`, `ui/skeleton`, `ui/gradient-button` |

Guessing the set from the route tree would have stopped after wave one. Two files nothing had
rendered *before* this task fell out of the same walk — `tenant-gateway.tsx` and
`tenant.context.tsx`, both designed, declared and never mounted.

### The premise was slightly wrong, and that is where the work was

§LV.13 says the shell and its page header "stay declared and nothing reaches them". True of the
shell; **not** true of the header. `PageHero`'s console branch rendered `PageHeader`
plus `StickySaveBar`, so deleting the file meant collapsing that branch — which is what made this
a change to twenty-two live pages' chrome rather than a pure deletion. `Panel` was left
alone on purpose: **SH923** asks the package for the frosted panel that retires it, and renaming
what SH923 will delete is work thrown away. **SH932** carries the names, which no longer describe
what those components do.

### Eleven readers encoded the two-era model, and a deletion is where they go quietly green

This is the half worth reading before touching any of them. Sixteen assertions failed, in eleven
files, and **not one was a bug in the deletion** — each had "there are two eras" as its premise.
Three distinct endings, and choosing wrongly costs coverage rather than a red run:

- **Follow the rule to its new home.** SH46 (a single-tenant install must not advertise tenant
  admin) named `page-header.tsx` and `app-sidebar.tsx`; the *defect* moved to `bento-nav.ts`, so
  the reader did. Same for SH679's marketplace reachability. A reader deleted alongside the surface
  it happened to name is how a closed defect reopens.
- **Re-derive it.** `outside-shell-chrome.test.mjs` forbade imports needing a `SidebarProvider`.
  Both probes named deleted modules — a pattern nothing can ever match again, which is a reader
  reporting a clean tree for ever. The *class* survived one provider along: the bento shell mounts
  `UserProvider`, and `useCurrentUser` throws without it, on exactly the six routes outside the
  shell. Its floor of `>= 8` became a named list of those six, because six is small enough to read.
- **Delete it, and say why in its place.** SH890's header-chip test asserted the console's one
  shared chrome composed `vg-accent-chip`. No Shio source uses that class now; it is the package's.
  Re-pointing such a test at whichever file is shared this month asserts less while looking like
  more.

Two vacuity guards inverted rather than relaxed. The census required every chrome probe to *match*
a page; zero is now the correct answer, so the probe is exercised against synthetic source instead
— SH890's reduced-motion rule already worked that way, for the same reason. And `bento-home` gained
a check that the survey still walks `src/app/console` at all, since "the console home is absent" is
otherwise indistinguishable from a walk that stopped looking — which is the exact blindness SH898
was filed against.

### What the deletion cost, recorded rather than absorbed

`breadcrumb-key.test.mjs` went with `lib/breadcrumb-key.ts`, and it carried **SH716**'s assertion.
`cli/test/assertion-debt.test.mjs` caught it on the next run — an id arriving in the debt by a
route none of its groups described: *the assertion was deleted with the surface it guarded*. It is
a declared line now, naming **SH933**, because the retirement is half done: twenty-two live pages
still push crumbs into a provider nothing renders. **SH934** recorded the other loss —
`tenant-switcher.tsx` was deleted unreferenced, and it was the only caller of
`POST /tenant/{name}/switch` — and closed it; see below.

## Unreferenced and unneeded are different findings (SH934)

The reachability walk that drove SH910 was right about `tenant-switcher.tsx`: nothing rendered it.
It was also the only caller of `POST /tenant/{name}/switch`, and `ShVigletTenantResolver` reads the
tenant from the session, a CDA token or an OIDC claim and **never** from the URL. So a curator with
two memberships was left in whichever tenant their session held, with signing in again as the
workaround — and SH909 had dropped the `/{tenant}/console` prefix in the same block, so both levers
went at once.

**A deletion pass can only see "unreferenced".** That is not a reason to keep dead code; it is a
reason for the pass to end with a list of what it removed, which is how this one was caught in the
same commit rather than by a curator.

### It appears where there is somewhere to go

Two conditions, and the second is what the deleted control got wrong. `multiTenant` says the
*instance* runs more than one tenant. Membership size says *this reader* has somewhere to go: the
old switcher rendered whenever `/tenant/mine` returned anything, so a curator with exactly one
membership was offered a menu whose only row was the tenant they were already in — noise that looks
like a feature.

### The gate goes through the nav declaration, because a reader made it

`useMultiTenant()` is exported from `bento-nav.ts` and the switcher calls that rather than
`useDiscovery()`. Not tidiness: `tenant-nav-gating.test.mjs` fails on a **second** consumer of
`useDiscovery` anywhere under `src/`, and its message names this declaration as where to route
through. SH46's defect was two surfaces deciding "is this a SaaS install" by different means and
drifting; the rule survived the surfaces it was written about, and it steered this one before it
was written.

Switching reloads the page. The tenant is a session property and everything on screen was fetched
under the old one, so re-fetching piecemeal is a long tail of stale reads for a control used twice
a day.

## A field pair that outlived its migration, and the mislabel it was drawing (SH935)

`BentoNavItem` carries `fallbackRoute` (where a surface lives) and an optional `bentoRoute` (where
it moved), with `bentoNavTarget()` preferring the second — so a migration was one line in
`bento-nav.ts`. SH909 ended the migration by repointing `ROUTES.CONSOLE` at `/bento`, which moved
every constant the fallbacks are built from. Both fields have named the same path ever since, and
the preference still ran without being able to prefer anything.

**The reason this was a task and not a cleanup is what the palette does with the absence.** For an
entry with no `bentoRoute` the command palette renders a marker whose screen-reader label is
`bento.palette.opensConsole` — **"Opens in console"**. Sixteen of seventeen migrated surfaces were
announcing a destination that no longer exists. A wrong accessible name is worse than a stale
field: it is only visible to the readers least able to work around it.

`at(route)` writes the route into both fields, so each entry states it once. Collapsing the pair
into a single `route` is the package's call since it owns the type, so **SH935 ships as a partial**
with that half named in the remainder.

### The vacuous pass this uncovered

`bento-nav.test.mjs` had a test named "a migration is one line — bentoRoute is the only field that
flips". It counted entries carrying a `bentoRoute` and asserted each kept the route it moved from.
Once every entry declared its route through `at()`, the count read **0/17**, the loop it guarded
ran over an empty list, and the test stayed green while asserting nothing — printing a progress bar
that read like reassurance. It is replaced by an assertion that every entry routes through `at()`
**and** that `at()` writes both fields, which is the property the palette actually depends on; a
helper setting one field would otherwise satisfy the first half and restore the defect everywhere
at once. Falsified by doing exactly that.

`tenant-nav-gating.test.mjs` needed one change for the same reason: its SH46 pattern matched
`ROUTES.ADMIN_TENANTS,` immediately followed by `requires:`, and the helper puts a `)` between
them. The closing paren is now optional in the pattern; the **adjacency** is not, because the point
of that assertion is that the gate sits on the entry naming the route rather than merely somewhere
in the same file.

### A note on undoing a falsification

The one-line edit that proved the new assertion fires was reverted with `git checkout <file>`,
which discarded every uncommitted change in that file — the whole task. Committed work (SH934's
`useMultiTenant`) survived; an hour of SH935 did not. Undo a deliberate one-line break with the
inverse edit, not with a command scoped to the file.

## A retirement has two sides, and SH910 only did one (SH933)

Deleting the console page header removed the only thing that ever drew a breadcrumb. The
**producer** side outlived it: twenty-one pages went on calling `resetBreadcrumb` on every mount,
building label-and-href arrays for a provider with no consumer. Nothing failed, which is the whole
problem — the calls typechecked, the provider stored what it was given, and the next page copied
from any of them inherited the habit.

**Stripped, not restored.** The bento shell already states the design: "the breadcrumb bar is
console chrome, and the bento era puts a page's identity in its own hero." Every hero carries a
back link, and the one place a reader moves up and down a tree all day — the content browser —
draws its own path in its sub-toolbar. There was nothing left for a console-wide trail to do.

### What a codemod leaves behind, and what catches it

21 files, 140 deletions against 21 insertions, done by script and then read. Two safety nets did
most of the reviewing: `noUnusedLocals` caught the orphaned imports, and `react-hooks/exhaustive-deps`
would have caught a dependency array left wrong. **Neither catches a dead array**, and the codemod
left two: a variable that is only ever `push`ed to is "used" as far as the compiler is concerned,
so `object.children.page.tsx` kept an entire effect that built a trail and dropped it, and
`post.edit.page.tsx` kept the same block wrapped around a live `setParentFolderId` that had to
survive. Read the diff after a codemod; the compiler is checking a narrower question than you are.

### The lint, because the habit outlives the diff

`useBreadcrumb` is still exported by the design system, so an import of it compiles and would
restore this state silently. `breadcrumb-retired.test.mjs` matches the **provider and the hook by
name** rather than the word "breadcrumb", which would have forbidden the two surfaces that
legitimately draw one. The local re-export at `contexts/breadcrumb.context.tsx` is deleted so the
habitual import path no longer resolves.

**SH716 stays in the assertion debt, and its entry changed kind.** It is no longer "nobody has
written the assertion yet" but "the surface it would assert against does not exist": a trail cannot
have duplicate React keys when nothing renders a trail. The ledger still records SH716 as shipped,
so the line stays rather than being deleted.

## The rail was never the only place a signal can live (SH936)

The sidebar badged Agent Review when runs were waiting (SH207). After the cutover the rail draws
**sections**, so there was no entry to badge, and `ReviewPendingProvider` was left unmounted with a
reason written beside it: *"the badge has nowhere to appear until a hub does; mounting it now would
be a fetch nothing reads."*

That sentence was right about the rail and wrong about the conclusion. Two of the three homes it
implied are still unavailable — `BentoNavSection` carries **no badge field**, so a section badge is
a design-system change and a release; and the hub `areaRoute` is meant to open does not exist here,
since `/bento/admin` redirects to the user list. Waiting for either meant waiting for a page nobody
had scheduled while an agent wrote drafts nothing told a curator about.

**The header is the third home, and it is the right one rather than the leftover one.** The signal
is *work is waiting for you*: ambient, personal, and the vocabulary of a notification rather than of
navigation. It is visible from every surface, which is what makes it ambient at all, and it sits
beside the account menu and the tenant switch, which are the other two things in that header.

### Absent, not zero — and a 403 that looks like an empty queue

`pending <= 0` renders nothing, which is SH207's rule kept. A dimmed `0` in the header of every
screen is a number a reader learns to skip, and skipping it is what the badge existed to prevent.

A curator who may not open the queue also sees nothing, and gets there without a permission check:
the provider swallows the read error and reports 0, so a 403 on the count is indistinguishable from
an empty one. That is correct for an indicator and would be wrong for a page.

### Where the provider goes, and why that is the assertion

`ReviewPendingProvider` wraps both the header and the `Outlet`. The count is **read** by the badge
and **invalidated** by the review queue after an approve or a revert, and two subtrees can only
reach each other through a common ancestor — which is why the count was written as a context rather
than a fetch inside the indicator. `bento-review-pending.test.mjs` asserts the ordering by index
rather than by presence: a provider that wraps only the header would still contain the string.

## The check was walking another product's console (SH927, probe half)

SH927 was filed about redness: the GraphQL explorer's Monaco workers fall back to the main thread
under the Vite dev server and start normally from the jar, so `pnpm -C shio-react console:check` is
permanently red on two probes locally and green in CI. Going to reproduce it turned up something
worse in the same instrument.

**`--base` defaults to `http://localhost:5173`, which is not a Shio address.** It is *the* Vite
default, so on a machine holding more than one Viglet front end it is whichever dev server was
started last. Measured here: `:5173` was serving `turing-app/src/App.tsx` under
`<title>Turing ES Administration Console</title>`, and this check would have logged in,
screenshotted every route and written a report about it without one line saying so.

That is the more dangerous half. A check that is red for the environment gets skimmed; a check that
answers confidently about the wrong application gets **believed**.

### What identifies a server, and why the title

Two requests, before the API is asked anything and before a browser starts — everything after is
meaningless if this is the wrong front end, including the discovery call, which would provision a
fixture site for a console nobody is going to open.

| question | signal |
|---|---|
| dev server or jar? | Vite serves `/@vite/client` as JavaScript; the jar has no such route |
| is it Shio? | `<title>` in `index.html` — written by this repository and by nothing else |

`console-check-shape.test.mjs` holds the expected title against `index.html` itself, so a title
edited in one place turns the gate off loudly rather than quietly: a check that refuses *every*
origin reads exactly like a broken server.

**Verified in both directions, live.** Against `:5173` (Turing's dev server) it refuses and names
what it found; against a Shio dev server on `:5199` it prints
`server: vite dev server at http://localhost:5199 — "Viglet Shio CMS"` and goes on to fail at
discovery, which is a different failure and the correct one with no backend running.

### The refusal exits 1, and getting there took a measurement

The first version called `process.exit(1)`, which tears down the sockets the two identity requests
still hold. Windows aborts: `Assertion failed: !(handle->flags & UV_HANDLE_CLOSING)`, and the status
is **127**. A gate whose refusal exits 127 is one a CI script reads as *crashed* rather than as
*said no*. `process.exitCode` and a return; the reader forbids `process.exit(` by name.

### What this did not fix

**The workers still fall back under the dev server.** Reproducing it needs the explorer to actually
mount, which needs a backend serving a CDA token, and there was none running here — the page under
test never initialised Monaco, so the second artefact SH927 describes (an error event with no
message) was never observed. Writing a rule that forgives *unnamed* errors on the strength of a
report would mask real ones, which is the failure mode this whole file is written against. SH927
stays open for that half.

## What a curator has to relearn, and the two things nobody can (SH911)

The published documentation described the console this block replaced. Rewriting it is in
`vigletdocs/docs-shio` and is its own commit; what belongs here is what the rewrite **found**,
because two of the three findings are about the product rather than the prose.

**There were no screenshots.** §LV.14 asked for "every screenshot" to be retaken, and the
published Shio pages carry none — every image under them is an SVG diagram of a flow, not a
picture of a screen. The docs debt was entirely prose, which made it a smaller task than filed
and is worth recording so the next cutover does not budget for a photo session.

**The surface area of the change is small and specific.** Four claims went stale, not forty: the
address (`/console` → `/bento`), the sidebar (a rail carrying two areas, plus the palette that
replaces the list), the breadcrumb bar, and the dashboard's name. One thing that reads as stale is
not: **the content browser still draws its own path** in its sub-toolbar, so "click a folder to go
in, use the path to come back out" survived the header that used to carry a trail. Checking that
before rewriting is what kept the page true.

**Two entries are documented as losses**, which is the part a docs task is not supposed to produce.
The pending-review badge has no renderer under a rail that carries areas (**SH936**), and the
tenant switcher went with the sidebar (**SH934**). Both now carry a `:::caution` telling a reader
what to do instead — check the queue on a schedule, switch tenants through the API — because a page
that simply stopped mentioning them would read as though they had never existed.

## A count is the number acted on (SH953)

`object.children.page.tsx` looped the selection, awaited each `DELETE` with `.catch(() => {})`, and
then announced `content.itemsDeleted` with `selectedItems.size`. The select-all cap is a thousand
(SH237), so **a thousand refused deletes reported a thousand deleted** — and the folder write
service has real refusals, restore conflicts and 409s, none of which could reach the curator. The
single-row path had the same shape one line up: a refused delete still toasted "Deleted".

`src/lib/batch-delete.ts` holds the part worth asserting. `deleteEach` takes the call as an
argument and returns `{ deleted, attempted, refusals }`; `problemOf` reads the server's RFC 9457
document (`title` + `detail`, falling back to Spring's `message`, then to the rejection's own
message, so a network failure still says something true); `refusalSummary` caps the list and says
how many it is not showing, because a truncated list that does not admit it reads as the whole of
what went wrong.

**It is a module rather than a page test on purpose.** The page is a dialog, a toast and a refresh;
a test that renders all three to find out whether a number is right is a test nobody keeps. What
was wrong was the arithmetic and the swallowing, and both live in the module. Four lines of the
page's own half — the toast counting `outcome.deleted`, and the refresh — are asserted by reading
the file, which is the only place that substitution can be caught.

Two smaller decisions. A refused row **stays selected**, so the curator can read the reason and
retry without hunting for it again. And `refreshList` re-fetches `listData.page` rather than page
zero: a batch delete on page 14 used to answer by scrolling back to the top of the tree, which is
exactly where the refused rows are not.

When Block BD's bulk endpoint lands the loop becomes one call, and this reporting is what stays.

## Errors are instructions, on both sides of the wall (SH955)

The backend writes RFC 9457 problem documents carrying `fix`, `allowed`, `didYouMean` and `at`, and
`ShProblem` calls a 4xx with none of them incomplete. **Twenty-four console save paths caught the
rejection and toasted the word "Error"** — three of them in the post editor, in `catch` blocks that
did not even bind the rejection. The axios interceptor handled CSRF capture and backend-offline
reporting and nothing else, so no code path read `application/problem+json`. One refusal taught an
agent exactly what to do and told a curator nothing; P6 is a law about errors, not about the agent
surface, and it held on one side only.

`src/lib/problem.ts` is the one reader. `problemOf` parses the document; `headlineOf` prefers
`detail` over `title` (the long form is what a person reads first, the title is a label); `adviceOf`
joins `fix`, `didYouMean` and a capped `allowed` into the second line; `fieldOf` turns `data.furl`
into `furl` so a form can match it against its own fields. `reportProblem(error, fallback)` is the
single replacement for the fixed-string toast, and **the fallback is a floor**: where a rejection
carries nothing, it says exactly what the old line said, so this can never say less.

**One reader, not two.** SH953's `batch-delete.ts` grew a small `problemOf` a task earlier; it now
delegates here rather than keeping a second answer to "what did the server say". Its import is
`"./problem.ts"` — relative and extension-bearing, not the `@/` alias every page uses — because
`node --test` resolves no Vite alias, and the alias took that suite from green to
`ERR_MODULE_NOT_FOUND`. Vite resolves this form too, so one spelling serves both.

`problem-reporting.test.mjs` holds the lint: a save path reporting the fixed `common.error` string
fails the build. Comments are stripped first, or this file's own prose about the call would read as
the call — SH952's reader made exactly that mistake.

**The backend half, which gave `fieldOf` something to read.** `at` was set in exactly two places
before this, and one of them is the batch, which sets `ops[3]` — a form cannot match an op index
against anything, so a conflict over a friendly URL floated above a page with sixteen inputs. The
four refusals a curator meets while typing now carry it: the restore URL and title conflicts, the
move URL conflict (`data.furl`, `data.title`) and the site furl conflict — which carries plain
`furl`, not `data.furl`, because a site's own field is not a post's and a page with both would pin
it on the wrong input.

`PostFormFields` takes a `problem` prop and marks that one field: a destructive border and the
message under the widget, with `role="alert"` so it is announced rather than only coloured.
**Not `aria-invalid`** — the wrapper is a div, not a form control, and marking it invalid would
tell a screen reader the wrong thing about what it is. (Writing it that way also ran into SH893's
lint, whose regex refuses the exact ternary its own message recommends; filed as SH1074.) The
editor clears the mark on the next save, or a corrected field keeps its red border and the form
lies in the other direction.

`ShTeachingProblemLintTest` holds both halves on the server: every `ShProblem` chains a `fix` — all
141 of them already did, and nothing asserted it, so the 142nd was free to ship without one — and
the four field-specific refusals carry the pointer, asserted by construction because "is this
problem about a field" is a judgement no regex makes.

**Not covered:** `didYouMean` and `allowed` render as text in the toast rather than as a clickable
correction and a collapsible list. Block AX routes these reports into the assistant dock.
