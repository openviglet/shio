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
