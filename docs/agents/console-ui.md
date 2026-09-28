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
| `Relator` | Repeatable rows: add, remove with undo, duplicate, collapse, reorder by drag or arrow keys (SH1012) | `shChildrenRelatorItems` |
| `Blocks` | Typed block body: insert menu, reorder by drag or arrow keys, marks from the selection, paste as blocks (SH1017) | `blocksValue` |
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

## The console today: one shell, one chrome, pages by domain

This is what Blocks AQ, AR and AW left, stated as what is true now. How it got here — the parallel
route tree, the slice-by-slice migration, the cutover and the sweep — is in `git log` and in the
CHANGELOG entries SH886–SH911 and SH957–SH962, which is where this project keeps history.

**One shell.** `src/app/system/bento.root.page.tsx` is mounted at `ROUTES.BENTO` (`/bento`), and
every surface a curator navigates to is inside it. Old `/console/**` bookmarks — bare or
tenant-prefixed — redirect into it (SH909); `bento-is-the-default.test.mjs` refuses a second shell
and a route constant pointing anywhere else. Outside the shell there are six mounts, named in
`outside-shell-chrome.test.mjs`: login, setup, the design system's vocabulary harness, the legacy
redirect, the 404 at App.tsx's bare `*`, and the Universal Editor — which **owns the viewport**, so
inside the shell's 64px sticky header its iframe measured 48px below the fold (SH907). Nothing
mounted outside the shell may need a provider only the shell mounts: `useCurrentUser()` throws
without `UserProvider`, a white screen behind the error boundary (SH908).

**One chrome.** One `SectionCardChromeProvider chrome="bento"` encloses `<Routes>`, because the
design system's context still defaults to `console` (SH958); no route carries its own, and nothing
under `src/` calls `useSectionChrome()` (SH959). Both go when the package drops the switch (VDS146,
SH963). `chrome-vocabulary-census.test.mjs` holds what is left of that vocabulary, and it is only
that switch.

**Pages by domain.** `src/app/content`, `admin`, `editor` and `system` — the nav's own sections
(SH961). A new page goes where its surface is in the nav.

**How a page is built.** Two shapes, chosen by the page rather than by taste:

- **Entity-shaped pages take the package's page shapes.** `BentoEntityShell` for something whose
  identity is a title and a description, `BentoListPage` for a mosaic with a create tile, and this
  console's `AdminListPage` and `IdentityEntityPage` over them for the admin lists and for groups and
  roles. A user is not an entity-shaped page: its identity is a login key, and a hero that
  inline-edits `title` would rename it on blur (SH904).
- **Large working surfaces compose the seams**, so their logic exists once — the content browser,
  the post and post-type editors, the infrastructure editors, review, activity, marketplace:
  - `PageHero` for the header, and for the save bar when the page has one: it renders
    `BentoFormHero`, which owns both halves of the morph (SH901). Its `urlBase` is the page's
    **real parent** in the rail's tree, never the nearest root constant: a rail surface goes to
    `ROUTES.HOME` labelled "Home", an editor to its own list, a folder to its parent listing, a
    translation or language board to its post or site. `/bento/content` is the site list and
    `/bento/admin` only redirects to users, so neither is a parent of anything else
    (`hero-back-links.test.mjs`, SH1189);
  - `Panel` for a panel with nothing to title — a count, a toolbar, a listing;
  - `AdaptiveSectionCard` for a titled section, with `StaticHeader`/`Header` and `Content` as its
    **direct** children. Without a header it renders a plain card rather than the frosted section,
    which is the defect `checkBentoSurface` found on four panels (SH902); wrapping `Content` in
    anything renders an empty section (SH892). `chrome-adapter.test.mjs` asserts both;
  - `EmptyState` for "nothing here", with a message per use; a hand-rolled `py-16` void fails
    `empty-state.test.mjs` (SH903).

**Rules the route tree and the forms carry**, each with a reader:

- A shell's `formId` must be the id of a `<form>` its render prop declares, or its Save issues no
  request; an entity that is all identity is `autosaveOnly` and renders no Save at all (SH899, SH900).
- `new` is a **param value**, not a static sibling: a static `admin/groups/new` beside `:groupId`
  matches first with no param, and the page renders `null` (SH904).
- A route's param is named what its component reads from `useParams()`, or the page renders blank
  with no error — `bento-admin-slice.test.mjs` pairs them (SH905).
- A tile a curator activates is a real button, reachable by keyboard (SH906).
- No page composes shadcn's `Card`: `surface-digest.test.mjs` refuses it (SH960).

The curator-facing half of the change is documented in `vigletdocs/docs-shio` (SH911); a published
page that still describes a second console look is a docs defect.

## The surface digest — proving a page did not change without opening it (SH896, SH960)

`shio-react/scripts/surface-digest.mjs` is the console's analogue of SH101's render digest, and
`pnpm --filter shio-react surface:digest` prints one line per surface. It reads **source, not a
rendered page**: "the instance does not run a browser" is a standing non-goal, and a console page
resolves a router, three contexts and a service call before it paints, so rendering one to prove it
composes would mean standing up most of the app.

It covers two things and nothing else: the **components a page composes, in source order**, and the
**surface classes** that carry the frosted panel and its lift (`backdrop-blur`, `bg-card`, `shadow`,
`rounded`, `border`, `ring`, …). It walks the four page trees in `DOMAINS`, keyed by path under
`src/app`.

```
node scripts/surface-digest.mjs --json > before.json
#   ... change something ...
node scripts/surface-digest.mjs --against before.json   # exits 1, naming page and field
```

**It is not a pixel claim.** A matching digest says the same components were composed in the same
order with the same surface vocabulary; it does not say the page looks right. What it buys is the
other direction: when a digest moves, something structural moved, and the report names the page and
the field.

**The vocabulary is measured, not listed from memory (SH960).** It names the package's components
*and* this console's seams — `PageHero`, `Panel`, `EmptyState`, `AdaptiveSectionCard`,
`AdminListPage`, `IdentityEntityPage` — because that is what most pages compose; with the package's
names alone, 33 of 45 surfaces digested nothing. Today one surface composes none of them, the visual
editor, and `surface-digest.test.mjs` holds that as a named list, so a page the vocabulary cannot
see fails by name. The `Card` family stays in it for one reason: `@/components/ui/card` still
resolves, and the same test refuses any page that composes it. There is **no era field**: it
classified by import, and labelled bento pages `console` (33 of 45) once the console had one chrome.

**Both failure modes are asserted, because they pull against each other.** A digest that moves on
*everything* — reindenting, a renamed handler, a new comment, an unused import — is noise, and a
reviewer learns within a week to re-baseline without reading it. A digest that moves on *nothing* is
green across the change it exists to witness. So the test asserts stability under reformatting,
comments, renamed locals and an unused import, **and** movement on a swapped component, a reordered
hero and a changed surface class.

One deliberate sharp edge: the sort is **codepoint, never `localeCompare`**. The digest hashes that
order, so a locale-dependent comparator would hash one page to two values on two machines.

## The bento layer, installed and proved here (SH887)

`@viglet/viglet-design-system/bento` is now imported by this console, and one page —
[`src/app/system/bento-vocabulary.page.tsx`](../../shio-react/src/app/system/bento-vocabulary.page.tsx)
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

**A page outside the shell must paint its own ground.** `<html>` carries `dark` from
`ThemeProvider`'s default while `<body>` keeps `shio-light-boot` — the design system's
boot-loader plugin picks light when nothing is stored under `shio-ui-theme`, which is every
first visit, and nothing removes the class once React mounts. Every console page hides it by
painting `bg-background` over the viewport. The harness renders `bg-background text-foreground
min-h-screen` for that reason and not for decoration; **SH918** fixes the disagreement itself,
and every page mounted outside the shell inherits exactly this.

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
stay readable, which is the only thing it has going for it. A product page goes through `t()`.

## The design system arrives twice: a plugin and a vendored copy (SH944)

`.claude/settings.json` enables `viglet-ds@viglet-design-system`, and the split follows what the
package already decided (VDS135). **The plugin** carries the `viglet-ds-pages` skill. It also
brings the duplicate-guard hook, which denies a write that declares a component the package
already exports and names the import instead, and `/viglet-ds-check`, which runs the package's own
gates. **The vendored copy** is only `docs/design/`, the artboards a plugin cannot put in the file
tree. It is written by `viglet-ds-page-reference` and gated by SH941/SH942. The bin reads
`enabledPlugins`, and once the plugin is on it removes the vendored skill as an orphan, because two
skills named `viglet-ds-pages` is the collision SH949 forbids.

Measured, not assumed: the hook and the command resolve the package from the file or package they
are given, so both work in `shio-react/`. The MCP server the skill points at starts as
`npx --no-install viglet-ds-mcp` at the project root, and here the package lives in the
`shio-react` workspace, so that command answers `E404`. The skill's fallback,
`node_modules/@viglet/viglet-design-system/docs/`, is also missing at the root. Handling a
consumer that installs the package in a workspace is the design system's job, and it is filed in
that repository's roadmap. Until it lands, the contract is at
`shio-react/node_modules/@viglet/viglet-design-system/docs/`.
`cli/test/design-system-plugin.test.mjs` holds the enable, the marketplace and the split.

## Shio's accent is a token, and the rest is a named debt (SH890)

The console's orange is declared **once**, on `:root` in
[`src/index.css`](../../shio-react/src/index.css), as the four source tokens VDS23 made the
whole re-keying contract:

| Token | Value | Was |
|---|---|---|
| `--vg-accent-from` | `oklch(55.3% 0.195 38.402)` — `orange-700` (SH1051) | `orange-500` |
| `--vg-accent-to` | `oklch(55.5% 0.163 48.998)` — `amber-700` (SH1051) | `amber-500` |
| `--vg-accent-text` | `oklch(55.3% 0.195 38.402)` — `orange-700` (SH1051) | `orange-600` |
| `--vg-accent-text-dark` | `oklch(75% 0.183 55.934)` | `orange-400` |

**Measured, not asserted, and primary claimed (SH1051).** The values above were the console's old
Tailwind classes, and nobody had measured them. `viglet-ds-page-lint --contrast` (VDS137) put
orange-600 text at 3.59:1 on the light ground and white on the orange-500 → amber-500 button at
3.89:1 and 3.00:1, all under AA's 4.5. The fills and the light-ground text moved to the 700s, and the
dark ground keeps orange-400 (7.53:1). Primary was never claimed, so the rail's "you are here" marker
and the default button rendered the preset's neutral. `:root` now sets the four
`--vg-primary-*-base` inputs, never `--vg-primary` itself: orange-700 on white text for the light
ground, orange-400 on near-black for the dark. `test/accent-contrast.test.mjs` runs the measurement
on every `pnpm test`. It also reads the claimable families from the installed preset, so a family
the package adds fails Shio's build instead of rendering its neutral here.

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

A named map and not a ceiling: a ceiling lets one fixed page fund leaving the next alone. A page
that stops respelling the hue deletes its line in the same commit; a page that adds an orange fails
by name. `amber` is deliberately not counted —
that is the semantic warning colour and it means "careful", not "Shio".

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

### The provider's own script is inert, and says so (SH1164)

The design system's `ThemeProvider` is a wrapper over `next-themes`, which renders a blocking
`<script>` for a server render's first paint. This console is never server-rendered, a script
React inserts on the client never runs, and first paint is the boot loader's (above). React's
**development** build logs every executable `<script>` it renders as an error, so
`console-check.mjs` against the dev server failed **every route** on it, which hid whichever
route also had a real failure. The jar ships the production build and never showed it.

`App.tsx` passes `scriptProps={INERT_THEME_SCRIPT}`, a non-JavaScript `type` that React reads as
a data block. `test/theme-script-inert.test.mjs` asserts both halves: that `App.tsx` passes it,
and, rendered, that `next-themes` carries it onto the element. The other consoles on this design
system have the same script, and the design system is where their fix belongs.

## Every surface declared once, for both readers (SH889)

[`bento-nav.ts`](../../shio-react/src/app/system/bento-nav.ts) declares **17 surfaces in 2
sections**, and the rail *and* the command palette both read it. The rail takes
`useBentoNav()` (grouped), the palette takes `useBentoNavItems()` (flat). A palette built from
its own array would look completely fine right up to the release where one of them lists a
surface the other does not — that is the defect §LIV.4 was filed against.

| Field | Whose | Notes |
|---|---|---|
| `titleKey` | reused | Always the key the surface's own page already renders — the authoring contract's rule against a parallel namespace |
| `descriptionKey` | new (`nav.desc.*`) | A palette row and a hub tile show a sentence, which no page had. `en` + `pt` together |
| `fallbackRoute` / `bentoRoute` | both, via `at(route)` | The package's type carries two fields; `at()` writes the one route into both (SH935) |
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

**The shell's keys are the package's, bound once (SH1097).** `useBentoShellShortcuts` binds
`BENTO_SHELL_SHORTCUTS` (VDS149) — Ctrl/Cmd+K toggles the palette, a bare `/` opens it only
outside a field, `?` opens the shortcuts sheet — and `BentoShortcutsDialog` lists that same set.
SH889 bound the palette by hand, and the sheet then advertised a `?` nothing bound and omitted
`/`. Never add a `keydown` handler for these keys in the shell: `shell-shortcuts.test.mjs` fails
on one, because a second keymap is a sheet that lies.

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

The six in **shared components** are fixed, because a component's missing name is missing on every page that renders it: the permissions
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

**Stripped, not restored.** The shell already states the design: a page's identity is in its own
hero, so there is no breadcrumb bar. Every hero carries a
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

SH936 gave agent work an ambient signal after the sidebar went: a pending-review count in the header,
read by a badge and invalidated by the review queue through `ReviewPendingProvider`. SH968 replaced
it with the dock, and the badge, the provider and `bento-review-pending.test.mjs` are gone. What
SH936 was for still holds and is asserted in `agent-runs.test.mjs`: an agent that writes must be
told to the curator, and the pending count is still read once at load, so a curator who arrives
after a run hears about it. See "Agent runs report in the dock" below.

## A request that never connected is named (SH1170)

`console-check.mjs` recorded failing requests only from `response` events of 400 or more. A connection
that timed out has no response, and the console message it raises carries no URL. Over three runs,
two to five probes failed on `net::ERR_CONNECTION_TIMED_OUT`, on different routes each time, and
nothing said which resource. The check now also listens for `requestfailed` and records
`failed <METHOD> <path> (<errorText>)` in the probe's `failedRequests`. `net::ERR_ABORTED` is
skipped because the console aborts superseded and unmounted reads on purpose (SH1047, SH1116). Across
72 probes the listener added no false failures. `console-check-shape.test.mjs` holds both halves.

## The Universal Editor drive hears what Publish answered (SH1174)

Against a jar, the drive failed on "the editor's Publish reported nothing" and printed "publish
skipped: undefined". Neither was true. The write succeeded, and the success was shown twice: once
in the toast and once in the assistant dock (SH965). A locator matching two elements makes
Playwright's `waitFor` throw a strict-mode error, and the probe's `catch(() => false)` read that as
silence. The drive now matches with `.first()`. It also listens for the `post-unified` PATCH or
POST around the click and records each response's status and problem `type`, because a real refusal
goes to the dock rather than a toast and is recorded nowhere else. The summary line reports
"published", "publish refused: <status type>" or "publish skipped: <reason>". A failed publish also
saves `universal-editor-drive-publish.png`, and that screenshot is what exposed SH1175.

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
tell a screen reader the wrong thing about what it is. (Writing it that way also looked like it
ran into SH893's lint refusing the exact ternary its own message recommends. That was filed as
SH1074, but SH985 had already fixed it: `isStringLiteral` in `bento-a11y.test.mjs` accepts a
ternary whose branches are both quoted, and a fixture pins both directions.) The
editor clears the mark on the next save, or a corrected field keeps its red border and the form
lies in the other direction.

`ShTeachingProblemLintTest` holds both halves on the server: every `ShProblem` chains a `fix` — all
141 of them already did, and nothing asserted it, so the 142nd was free to ship without one — and
the four field-specific refusals carry the pointer, asserted by construction because "is this
problem about a field" is a judgement no regex makes.

**Not covered:** `didYouMean` and `allowed` render as text in the toast rather than as a clickable
correction and a collapsible list. Block AX routes these reports into the assistant dock.

## The two-chrome vocabulary, counted until it is gone (SH957)

`shio-react/test/chrome-vocabulary-census.test.mjs` holds `STILL_TWO_CHROMES`: every file that
still names the vocabulary of a console with two chromes, with its count of lines, asserted equal to
what the walk finds. A list, not a ceiling — a ceiling lets one task's deletions fund another's
additions — so a new reference fails naming its file, and a removal fails until its line is lowered
in the same commit. It prints both counts on every run.

`test/support/chrome-vocabulary.mjs` owns the walk (`src`, `test`, `scripts`, `e2e`) and five
probes, so a measurement and the assertion cannot read two rules:

- `SectionCardChromeProvider` and `useSectionChrome`, anywhere;
- a comparison against `"bento"`/`"console"` and a `chrome=` prop, in code only;
- a comment naming a chrome **beside an era word** (console, bento, era, both, two, either,
  neither, adapter, provider) — a heuristic, stated as one, which separates vocabulary from the
  ordinary sense of the word.

**Measured first, and the hand count was wrong again**: the roadmap line said 108 source lines and
180 in tests; the first run read 89 and 81, because the hand figure counted uses a one-chrome console
keeps. Block AW took it to **8 source lines and 34 test lines**, and what remains is the design
system's switch itself — the root provider, the vocabulary harness's specimen, the tests that hold
the provider and the package's two-branch `AdaptiveSectionCard`, and the check that nothing reads
the hook. None of it can leave before the package drops the switch (VDS146); **the list reaching
zero is Block AW's criterion**, and SH963 is the line that gets it there.

Non-vacuity is a copy of a real clean file (`ui/button.tsx`) in a temp root, read clean and then
named once a provider is planted in it.

## Every surface says how an agent reaches it (SH978)

`bento-nav.ts` is the console's map, and its own note says the rail and the palette both read it
because "declared twice they disagree within a release". The manifest and `tools/list` were not among
its readers, and nothing in `shio-app` could read it at all — it is TypeScript. `ShAgentP1ConformanceTest`
is keyed on manifest `features`, and SH463 already recorded what that means: a capability with no flag
is not classified. So a round could add console surface after console surface with every instrument
green, while P1 — a console-only feature is incomplete — was broken once per surface.

**`shio-app/src/main/resources/console/surfaces.json`** is the backend's half of that map: per surface
an id, a section, a route, the capability, and either an **agent equivalent** — a tool, a batch op or
a declared endpoint — or an **exception** carrying a reason and a `closedBy`. Icons, titles and i18n
keys stay in `bento-nav.ts`, where they belong; this is the half a JVM has to be able to read.

**`ShConsoleSurfaceP1Test`** joins the two. It reads the ids out of `bento-nav.ts` as text and asserts
the two sets are equal, so a surface added on one side and not the other fails the build. It resolves
every agent equivalent against the running server — the tool registry, `ShAgentBatchService.OPS`, the
manifest's endpoint index — so a pointer at something that does not exist is a red build rather than a
sentence. And it prints the inventory: on the first run, 17 surfaces, four answered by a tool, three by
a declared endpoint, one by an op, and nine classified — six operator-only, one navigation hub, one
REST-only, and **one gap**: the activity trail, which is the same hole SH973 found from the controller
side and SH1005 is the line that closes.

The exception vocabulary is the smallest today's surfaces need. SH980 is where it is defined properly,
and this test is what will hold it.

## Four readers of one declaration (SH979)

SH978 made the surface declaration exist and be checked. A declaration with one reader is still a
second list: the rail, the palette, the manifest and MCP would each have gone on describing the
console their own way, which is the state the file was written to end. So four readers take it:

- **The console.** `bento-nav.ts` imports `console/surfaces.json` and keeps only what TypeScript
  owns — the icon, the two i18n keys, the tile's tone, the `ROUTES` constant and the visibility
  gate — in a `DECORATION` table keyed by the declared id. A surface declared and not decorated
  throws at module load; a route that disagrees with the one declared fails `bento-nav.test.mjs`.
  The route is the one field on both sides on purpose: the router is built from `ROUTES`, and the
  other three readers have no access to TypeScript constants, so the two are asserted equal rather
  than one derived from the other.
- **MCP**, as `shio://surfaces` — a resource rather than a tool, because it is a document about what
  a *curator* can do, read once, and `tools/list` is re-sent every turn (P3).
- **The manifest**, behind `?include=surfaces`. Opt-in, and the only section that is: `all` does not
  include it either. An ordinary manifest read is the first read of every session and the one an
  ETag makes free, so seventeen rows about the console on every one of them is exactly the cost
  `include=` exists to remove.
- **The CLI**, as `shio surfaces` — the list with the answer beside each row, which is how "is there
  an agent way to do what that screen does" becomes a command rather than a question for whoever
  wrote the screen. `--json` for a pipeline.

`ShConsoleSurfaceP1Test` holds the manifest's default unchanged, the resource's order equal to the
console's, and the resource's HTTP twin pointing at the include.

## Words a gate can read for a surface with no agent leg (SH980)

SH978's gate refuses an unclassified surface, and several things the console does are console-only by
nature: the shell, the audit log's readable column, the query cache, the error boundaries, the widget
labelling, the skip link, a curator's dock preferences. Each of those tasks argued its P1 position in
a paragraph — which is exactly what the gate cannot read, so either it landed red for a whole round
or every task invented its own wording, which is the drift between two authorities `agents.md`
already names.

`console/surfaces.json` now declares the vocabulary in the document all four readers hold. Nine
words, each with what classifying something that way **means** and what would **falsify** it:
`operator-only`, `navigation`, `rest-only`, `gap`, `chrome`, `accessibility`, `cache`, `preference`
and `contract-leg`. A word with no falsifier is a place to put things rather than a classification,
so the gate refuses one.

Two lists use those words. A **surface** with no agent equivalent names one in its `exception`, and
only `gap` may carry a `closedBy` — every other reason is falsified by its own kind, stated once in
the vocabulary rather than per entry. **`consoleOnly`** is the same vocabulary applied to what is not
a surface at all: the root chrome provider, the reading column, the editor bridge, the query cache,
the error boundaries, the widget labelling, the skip link and the dock preferences, seeded so the
gate is green on the day it lands. A later console-only task adds one line there instead of an
argument.

`ShConsoleSurfaceP1Test` refuses a reason the file does not define, refuses a word nobody uses — a
vocabulary entry with no user is one nobody agreed on — and plants a surface whose reason is a
sentence to prove the refusal happens. The rule is written over a supplied list rather than over the
file, because a check that can only be exercised by breaking the document it guards is a check
nobody runs.

## Every wait on the design system, probed or declared unprobeable (SH984)

`design-system-waits.test.mjs` exists because *has it landed* was three reads across two repositories
that somebody had to remember to make (SH937). Its rule: a roadmap line waiting on a design-system
release carries an entry — what it wants, a probe that answers whether the wait is over, a proof that
the probe read something real, and the instruction to run when it fires. It has worked twice, on
2026.3.5 and on SH940's bump.

What it had not done is ask **how many lines are waiting**. Two entries stood while the backlog went
on filing them, so the list was a sample. It is now derived: the check reads `docs/ROADMAP.md` for
every line whose deps carry `DS VDS<n>` and fails naming any that has no entry. SH984 was filed
against seven such lines; the first run of that check found **thirteen**. Six had been invisible to
the read that filed the task, which is the ordinary result when a list is kept by hand and the
argument for not keeping one that way (SH142).

Three things the round settled, each of which had a wrong version that looked right:

- **A probe reads a name, not a word.** The skip-link probe first matched `skip`, and two bento
  declarations already say *"skip it and pass their own node"* and *"skipping to `h3`"* in their
  prose — so it reported that wait over on the day it was written. The shape-reading probes are now
  driven against the shape they wait for *and* against what is installed today, because `proof` cannot
  catch this: a read can reach the right file and still ask a question nothing answers yes to.
- **An exact match, not a substring.** The package already emits `BentoShellFormState`, so a probe
  asking whether anything *contains* `BentoShell` ends SH946's wait immediately.
- **An entry may decline to probe, and must say so.** SH948 waits on `use:local` resolving consumers
  from a register — and `use:local` runs from the design system's own checkout, so nothing in the
  published package changes on the day it lands. Reading the checkout is the one thing this file must
  not do: that path exists on one machine and CI would go red on it. The entry carries
  `unprobeable` with its reason instead, and the version pin is what ends that wait. A wait with no
  entry is invisible; a wait whose entry lies is worse, because it is trusted.

## What is scheduled, as a page, and the zone a schedule is read in (SH991)

**Scheduled** (`/bento/content/scheduled`) lists what goes live or comes down, earliest first. It is
declared in `console/surfaces.json` with `shio_find` as its agent leg, because it reads exactly what an
agent reads: `ShScheduleService` walks `/api/v2/agent/find?scheduled=any&ids=true` to the end and sorts
by instant, since find sorts by address (its page cursor). A post due to go live and come down is two
rows, each linking to the post's editor. It is the Activity page's shape — a read-mostly table under a
`PageHero` — because an agenda is read down a column.

The times are the reader's local time, and **the page and the schedule dialog both say which zone that
is**: the dialog's `datetime-local` inputs are local time against an instant the server stores, and
nothing used to say whose local time. `scheduled-surface.test.mjs` holds the declaration, the route,
the one listing, and the zone line in both places in both locales.

The design's month and week calendar, with rescheduling by drag, is SH1082: this console has no
calendar component (the design-system section above lists it as unavailable), and a hand-rolled one
is the second copy the no-duplicate-components gate exists to refuse.

## Publish, when the curator may not (SH995)

The post editor asks `GET /api/v2/object/{id}/allowed` once per post and disables **Publish** and
**Unpublish** when the answer says no, carrying `publishHint` as the control's title and as a line
beside it. Asked rather than assumed: the alternative is offering a button and answering it with a
403, which tells somebody they cannot do a thing only after they have tried. A permission read that
fails leaves the buttons as they were — the server is what decides, and hiding a control because a
side call broke is the worse guess.

**The sentence comes from the server** (`ShContentAclService.Allowed#publishHint`, the same words its
refusal uses) and the console interpolates it rather than writing its own: two phrasings of one rule
is two authorisation stories, and only one of them is enforced. The i18n key takes `{{reason}}` for
exactly that reason.

**Agent Review's Approve is deliberately NOT disabled**, which is where this departs from SH995's
design. Approval is per post — a run touches many, and a curator may hold PUBLISH on some branches of
the tree and not others — so a disabled button would hide a partially permitted run. The server
already publishes what it may and returns every refusal as a skipped row carrying the server's own
sentence, which the page raises one toast per post for. The permissions dialog offers the third
level, `PUBLISH`, in both locales.

## The publish override, and why the box appears only after the refusal (SH998)

A reason box on every publish would train a curator to fill one in, which is how a waiver stops meaning
anything. So `PublishPreviewDialog` shows one **only** once the server has refused: `handlePublish`
catches the 409, keeps the dialog open and puts the gate's own sentence in it, and the confirm button
becomes "publish past the check" and stays disabled until something is typed — the server refuses a
blank waiver, and the button knowing that is what stops a curator learning the rule from a second
refusal.

The branch is on `problem.type` (which `problemOf` now reads off the `application/problem+json` body),
never on the refusal's prose. The prose is written for the curator and is meant to be improvable; a
console that decided what to offer by matching a sentence would break the day somebody improved one.
`Problem.type` is the only field on that document a caller should branch on, and it is the reason it
is now carried.

## The Activity page says which changes were a machine's (SH999)

An agent chip in the Who column, beside the principal rather than instead of it — both are true, and
a column showing only the name said the same thing about a machine's write and a person's. It
carries `agentName` where the run gave one and the first eight characters of the session where it
did not, and links to `/admin/review?session=<id>`, which the queue opens on arrival for a session it
still lists. Reading that run and undoing it is the next thing a curator who noticed the chip wants.

Three buttons — Everyone / Agents / Curators — rather than a select, because the whole vocabulary is
three words. The split is the **server's** (`?agent=agent|curator`), never computed here: a console
deciding for itself which rows were an agent's would be a second answer to a question
`/agent/changes` already answers, and it would get the SH996 console session wrong the first time.

## The review queue leads with the premise (SH1000)

The session row's headline is the intent; the change count, the actor and the run's name move to the
line below it. A reviewer who knows what a run was for reads eleven changes as one decision, and
without it they read the diff to find out why. A run that declared none says so in those words and
names `--intent`, in both locales — an absent premise is a fact about the run.

## What a run changed the look of (SH1000)

The session row gains **counts by site name** beside its counts by action, and only when a run
touched more than one: on a single-site run the badge repeats what the addresses already say, and a
badge that is always right and never news is one people stop reading.

The expanded detail gains **which of those pages look different**. That is the question the action
column cannot answer — an UPDATE that moved a headline and one that moved the whole page read
identically there. It is on the detail and not on the listing because each comparison is **two real
renders** (SH249): nothing stores the appearance a page had, so the "before" is the revision that
preceded the run rendered through today's templates, and the listing is asked for on every visit to
the console and holds every open run. Capped at twenty pages with the cut reported, and shown only
when something was comparable — "0 of 0 pages" is a sentence about the instrument, not about the
run. The count travels with the list because without the denominator an empty `moved` reads as
"nothing moved" when it may mean "nothing was comparable".

## Triaging a run, one change at a time (SH1001)

`ReviewTriage` is its own component rather than more of the queue page: the page renders a list of
runs, and this is a tool over one run's changes with a selection, a keyboard and a refusal of its
own. The two whole-run buttons stay, because "all of it" is a real answer and the common one.

**Shift-click takes a range** from the last box touched — a run is often one sweep, and eleven pages
of the same change should not be eleven clicks. **Reject is disabled until a reason is typed**,
because the server refuses one without and a curator should not learn the rule from a refusal; the
keyboard path refuses it too, or the rule would be a property of one button. `HOLD` takes no reason,
which is what keeps the requirement from taxing the act of parking something.

**Keyboard triage**: `j`/`k` move, `x` selects, `a`/`h` decide the selection — or the row under the
cursor when nothing is selected. `r` **focuses the reason box rather than rejecting**: the one
destructive key on this list must not be a keystroke away from acting. None of them fire while the
reason is being typed, or `a` in "the prices are wrong" would approve the page.

The decision is one request for the whole triage, and the page re-reads the run and the badge after
it (SH207): a page showing the old answer after an action is the one number a curator learns to
distrust. `review.service.ts` also stopped importing bare `axios` — it worked only because
`@/lib/axios` mutates the global defaults and something else had been imported first, so CSRF and
the base URL were arriving by import order.

## The note rail on the post editor (SH1003)

Under the fields, not in a dialog. A note is written **while** reading the page it is about — the
curator has just seen the wrong price — and a dialog would hide the thing being corrected behind the
box for correcting it, which is the one arrangement this feature cannot afford.

Open notes lead and resolved ones are behind a click: the list a curator opens this for is "what is
still being asked", and a rail showing every finished conversation grows into a page nobody scrolls,
which is how a comment thread stops being read. The `field` anchor is **carried by whoever mounts
the rail**, never inferred from focus — inferring it would give a different answer depending on what
the curator last clicked.

Two kinds in the composer, not three: a reply is a note under another one, and the rail threads by
page, so offering it would be a control that means nothing until a thread exists to reply into.

The two kinds are a **segmented radio group** (SH1186), not two buttons: `role="radiogroup"` on a
muted track, the picked kind raised, arrow keys switch. Drawn as two solid `Button`s beside the
submit they read as three actions, and the picked kind looked like the one that sends the note. The
submit is the only filled button on the row. There is no shared toggle-group in `components/ui`, so
the control lives in the rail until a second choice-of-few needs it.

## Unsaved work, and the guard this router cannot have (SH1011)

The post editor held its data in `useState` and never compared it with the post it loaded, so
nothing on the page knew the difference between "opened and read" and "typed into and not saved".
Closing the tab lost everything silently. The design system has rendered an **Unsaved changes** cue
on `dirty` since it shipped, and the one page that needed it passed only `titleMissing`.

`dirty` is **derived**, not tracked: `isDirty(post.data, data)` compares a canonical serialization —
recursively sorted keys, `null` folded to `""` — rather than a flag set on change and cleared on
save, which is one more thing that can be wrong in the direction that loses work. Two failures that
rule exists for, both of which make the cue useless by leaving it always on: a widget hands back the
same document with its keys in a different order, and a widget that clears its input hands back
`null` where the load had `""`. A list's order is *not* folded — a reordered list is a changed list.

**`beforeunload` is registered only while there is something to lose**, and removed when there is
not. A permanently-registered handler makes every reload of every clean page show the browser's
dialog, which is how a guard becomes the thing people click through without reading.

**The router blocks too, since SH1090.** React Router 7's `useBlocker` needs a data router, and
`main.tsx` used to mount `<BrowserRouter>`, so it threw and an in-app route change walked away from
unsaved work silently. `App.tsx` now builds the same `<Route>` JSX into a data router with
`createBrowserRouter(createRoutesFromElements(...))` under a pathless layout route (`AppLayout`,
holding the theme, query and toast providers) and renders `<RouterProvider>`. `ErrorBoundary` and
`BackendStatusProvider` stay above it in `main.tsx`; neither uses a router hook. `useUnsavedGuard`
arms `useBlocker` while dirty and asks with `confirm`, in the page's own words when it passes a
message (the Universal Editor names how many edits). It blocks only a change of **path**, so a list
writing its filters to the URL is never interrupted. The pages' own hand-written confirms before
Cancel and before leaving the editor are gone, because the blocker asks for those exits too and two
dialogs for one click is the defect. The route scanners in `spa-contexts.test.mjs` and
`outside-shell-chrome.test.mjs` treat a pathless, non-index `<Route>` as a layout, not a level. The
data router cost 55 KB raw / 17 KB gzip eager, recorded in `console-size-budget.json`.

### Autosave, and the buffer behind it (SH1011)

The draft is written after the typing stops — `useAutosave`, three seconds quiet, debounced on the
**value** of the changed fields rather than on their key list, because on the key list alone it
would fire three seconds after the *first* keystroke and call that "after the typing stops".

Three rules it will not bend:

- **Only the changed keys go on the wire.** `changedAttrs` diffs against the post as loaded and the
  `PATCH` names nothing else. The merge verb's rule is that a field *not* named keeps its value,
  which is worth something only if the caller names as little as it can — otherwise autosave
  overwrites another writer's change to a field this page never touched. Clearing a field is an
  edit, so a key present in the load and absent from the form is sent as `""`.
- **It writes the draft and only the draft.** Autosaving is a write nobody asked for, which is the
  shape P7 refuses, so it is confined to the row a visitor cannot see. Publish stays explicit.
- **The first refusal ends it.** A 409 means somebody else wrote; retrying is the overwrite the
  guard exists to prevent, and retrying silently on a timer is that overwrite with nobody watching.
  The save bar says *stopped* out loud — a silent autosave that has given up is worse than one that
  never ran. It is also paused while the curator's own save, publish or conflict dialog is in
  flight, or the page would race itself into SH985's refusal.

`draft-buffer.ts` keeps what is on screen in **IndexedDB** keyed by post id, covering what autosave
cannot: the seconds before the next write, and every case where the write cannot happen at all.
It is **offered, never restored** — a buffer that restored itself makes the page a second writer
with no name on it, and a curator who finds text they do not remember typing cannot tell whether it
is theirs, an agent's, or yesterday's. Every access is wrapped and a failure resolves to `null`: a
private window or a quota refusal is not an error worth a dialog, because the work is still on
screen. IndexedDB rather than `localStorage` because a rich-text body is not small and
`localStorage` is a few megabytes for the whole origin.

The CLI half SH1011 designed — `shio post set`, one field against a live instance — is **not**
here. It was written and withdrawn: the merge verb carrying `If-Match` refuses a cookieless caller
by a decision `ShStatelessReachabilityLintTest` records, the CDA's merge is stateless but has no
precondition at all, and the agent's only write declares whole state. SH1091 holds the choice.

## The repeatable field, and the keys it must not write (SH1012)

`RelatorWidget` destructured `attr` alone, so the `onChange` in its props was never called and it
told the curator editing was disabled — while the widget table above described add and remove.
Everything underneath was already complete: rows convert to and from `data`, the merge verb writes
them, the projection carries them. Only the console leg was missing.

It is now an array editor: add, remove **with undo**, duplicate, collapse with a headline taken from
the child marked `isTitle`, and reorder by drag **or arrow keys** (dnd-kit's `KeyboardSensor`, the
same wiring the post-type editor uses — drag alone is a feature some curators do not have).

**The part that is not about the widget.** `relatorItemToValue` used to write `_id`, `_ordinal` and
`_title`/`_summary` back into the stored row. A row of an SH95 collection is a plain map of its
declared child fields — `{title, body}` — which is exactly what lets `ShPostDraftMergeService`
address `features.1.body`. Those four keys were undeclared content waiting to be written, harmless
only because the widget could not save; making it editable without removing them is how this fix
becomes the defect. So identity is minted on read, held for React and dnd-kit, and **stripped on
write**; position is the ordinal and there is no second copy of it.

`extractValueFromAttr` also answers `[]` for an emptied repeatable. It used to fall through to
`strValue ?? null`, which made "the curator removed the last row" indistinguishable from "never
filled in" — the removal did not survive the save.

`widget-editability.test.mjs` is the guard: every row of the widget table not described as a
container or hidden must call `onChange`, and every name in the table must be one `WIDGET_MAP`
registers. It fails on the widget exactly as it shipped.

## Insights: the lint and the digest, on the page they are about (SH1013)

The instance computes a lint with a fix per finding (`/agent/verify`) and a structural digest of any
page (`/agent/render`), and the console asked for neither. A curator could not see that the page in
front of them had an image with no alt text or an outline that starts at `h3`, while `shio verify`
printed both to a terminal nobody had open.

`PostInsightsPanel` reads both, addressed by `id:<uuid>` — the escape hatch SH172 kept for a console
that holds an id — and scoped to `state=draft`, so it lints what is being edited rather than what
is live. Three groups, and the third is deliberately not a judgement: **Blockers** are the errors,
**Suggestions** the warnings, **Page shape** is the digest. Folding the digest in would make "this
page is 340 words" look like something to fix.

**Where the missing-alt count comes from, and why not from the linter.**
`ShAgentVerifyService.markup` scans **published rows only** and says so in as many words — *a
draft's broken link is work in progress*. That is right for a site-wide sweep and this does not
touch it. But the panel's subject is one page the curator is looking at, and the digest inventories
a draft's images with their alt text, so the same fact arrives by the read whose scope already
matches the question. `alt=""` is a decision and an absent `alt` is not, so only the second is
reported.

**It reads on demand and on the curator's own save — not on autosave's.** Both calls render the page
server-side; doing that every three seconds of typing is the cost P3 refuses, one layer out from
tokens. `setInsightsAt` is bumped in `handleSave` and `handlePublish` and nowhere else.

A finding that carries a `field` offers a button that scrolls to it and focuses the input;
`fieldAnchorId` is exported from `post-form-fields.tsx` so the two sides cannot spell the anchor
differently. A rule that knows no field — a broken link belongs to the rendered page, not to one box
— offers no button rather than one that lands somewhere arbitrary.

## The live preview, and the frame it renders into (SH1014)

`PostLivePreview` posts the form's current values to `/agent/render?format=html` and puts the markup
in an iframe. There is no URL that serves this page — the document does not exist anywhere, which
is the point — so it goes in by `srcDoc`.

**`sandbox="allow-scripts"`, and deliberately not `allow-same-origin`.** Scripts run because a page
whose scripts do not run is not what a visitor gets, which is the question being asked. Without
`allow-same-origin` the frame is a unique origin and cannot read the console's session, cookies or
storage — and the markup it shows is composed from content a caller supplied, so the console showing
it is exactly the thing it must not reach.

**Re-rendering as you type is a switch, and it starts off.** The design called for a debounced
re-render and it is here, but each one composes a whole page on the server — layout, regions, theme
— and a curator who opened the editor to fix a typo has not asked for one per pause in their typing.
P3 is a budget on tokens; this is the same argument about a heavier unit. Off, the frame still
refreshes on demand and on every save, which is the question being asked most of the time.

The panel says, under the frame, that nothing in it is saved. A preview that looks like the page is
exactly the thing a curator could mistake for having saved it.

**A type that is not a page is not asked for (SH1167).** A menu, a section or a theme has no layout
binding on its site, and the server refuses to render it with `render-no-layout-binding`. That 400 is
correct, but the browser logs every 4xx as a console error, so eleven editor routes opened on one.
`lib/layout-bindings.ts` mirrors `ShPageRenderService.bindingsOf` (the site's `postTypeLayout`, a
JSON object, string values only). The post editor reads the site under `id:<siteId>`, which is the
visual editor's key, so they share one cache entry, and passes `renderable`. `false` shows
`insights.previewNoLayout` and requests nothing. `undefined` means not known yet and waits. A failed
site read gives `true`, because a missing answer must not hide a preview that would have worked.
The prop is required and never defaulted: `renderable = true` in the destructuring turned the
caller's `undefined` into `true`, and the first version rendered every menu once before its site
answered.

**Not done:** the split view with a persisted ratio. The editor is a vertical stack of section
cards, and a two-pane layout is a change to the page's shape rather than an addition to it. The
preview is a card in that stack for now.

## The block editor, and why it is not a contentEditable (SH1017)

The widget `Blocks` replaces called `document.execCommand` for bold, italic, lists, link and code,
asked for every URL with `prompt()`, and had no paste handling — so a paste from a word processor
arrived as that processor's spans. Every one of those is a symptom of editing **markup** in the
browser's own editor and then trying to get a document back out.

SH1015 made the document a list of typed blocks whose inline emphasis is a range over the block's
own text. A range is exactly what a plain `<textarea>` already reports — `selectionStart` and
`selectionEnd` — so a mark is applied by **reading two numbers**, not by asking the browser to
mutate a DOM and then parsing what it did. The editor is therefore ordinary form controls, which is
also what makes every gesture keyboard-reachable without arranging for it.

**The cost is named rather than hidden:** emphasis is not shown bold while typing. It appears as a
removable chip under the field carrying the text it covers, and the page's live preview (SH1014)
renders the real thing. That is a worse WYSIWYG and a better document — and the document is what a
front end, an agent and a diff all read.

**Routing is by widget, never by the value's shape.** A blocks document and a relator's rows are
both arrays of objects, so `buildAttrFromValue` would have handed a body to the relator editor. A
`Blocks` field goes to its own `blocksValue` slot, and an emptied one answers `[]` rather than
`null` — removing the last block is an edit that has to survive the save.

**Accessibility is in the construction, not added after.** Every block is a `<section>` labelled
*Block 3 of 7, heading*; inserts, removals and moves are announced through a polite live region,
because a move has no visual anchor for a screen reader and the keyboard path is the one a
screen-reader user takes. Drag has ↑/↓ buttons as its equivalent.

**Paste is a document, not a string.** Clipboard HTML is parsed and only the shapes the model names
survive — headings, paragraphs, list items, quotes, code. A `<script>` produces no block, because a
shape with no name is not content this instance can store. Text is taken as `textContent`, so a
paste can never introduce a tag; text that *looks* like a tag is stored as the characters it is and
escaped by SH1016's renderer, which is the difference between storing markup and storing prose.

`lib/blocks.ts` mirrors the server's vocabulary so the editor offers only what `ShBlocks` accepts.
`blankBlock("heading")` sets `level` for that reason: an editor that can compose a document the
instance will refuse teaches its user a shape that does not exist.

## The content browser, operable without a mouse (SH1020)

The browser's rows carried no `role`, no `tabIndex`, no `aria-selected` and no `onKeyDown`, and its
**eight row actions** — edit, clone, copy, move, permissions, view, delete — lived inside a
`hidden group-hover:flex` block. A keyboard user could open a post and do nothing else to it; a
screen reader was never told the actions existed. The site list fixed exactly this in its own sweep
and the browser was not part of it.

**It is a listbox now.** `role="listbox"` with `aria-multiselectable` (the checkbox column was
always a multi-select — the semantics were what was missing), each row a `role="option"` carrying
`aria-selected`, and a **roving tabindex** so Tab moves past the list rather than through a
hundred rows. That last part is not a detail: a list that claims the role and does not move on
arrows is worse than one that claims nothing.

| Key | What it does |
|---|---|
| ↑ / ↓, Home / End | Move the focus ring |
| Space | Toggle this row |
| Shift + ↑/↓, Shift + click | Extend from the anchor |
| Enter | Open |
| Delete / Backspace | The confirm flow, never straight to the server |
| `/` | Focus search |

**The anchor is state, not the previous row.** Shift extends from where the selection last started,
which is the rule every file manager uses — and range selection is what makes a bulk verb a gesture
instead of a hundred clicks.

**`/` opens the field as well as focusing it**, because search sits behind a toggle and a binding
that only focused would do nothing on a page where nobody clicked the magnifier. It ignores the
keystroke while an `input`, `textarea` or `contenteditable` has focus — otherwise typing a slash
into a name jumps the cursor out of the box it was typed in, and the rich-text fields are exactly
where a slash gets typed.

The actions are now **data** (`rowActions(item)`) handed to `BentoActionsMenu`, with a trigger label
naming the row: eight identical "Actions" buttons is what a screen reader hears otherwise. Stating
the set in one place is a side benefit — what a row can do was previously readable only by scrolling
the JSX that drew it.

**A row is named by `rowLabel(item)`, the title plus its locale (SH1059).** The server sent `locale`
on every post row and the mapping into `ObjectChild` dropped it, so a page and its translation —
which the unique key permits by design — were two rows with the same title, date and type, and a
curator deleting "the duplicate" deleted a translation. The locale is a chip beside the post-type
badge, which is the badge two translations share, and `rowLabel` is what the checkbox, the title
button and the actions trigger all say, so the two rows differ to a screen reader exactly where they
differ on screen. Every post carries a locale (`en` by default), so every post row has a chip; a
folder has none and its label is its name alone. The chip does not mark the site's *default*
locale: the browser does not fetch the site's policy (`GET /api/v2/site/{id}/locales`, SH1056),
and SH1125 is filed for it.

**The translation workspace is `content/post/:postId/translate` (SH1061).** The whole translation
interface was a menu that navigated *away*: a translator read the English page, went to the
Portuguese one, and worked from memory or a second tab. The workspace puts them side by side —
source left, translation right — **aligned by the post-type's own field order**, so the two sides
say the same thing in the same place. The left pane renders text and not widgets, which is what
makes it read-only *by construction*: there is no input to click into, and editing English believing
it is Portuguese is the mistake the layout exists to prevent. Each field carries a
copy-from-source for the values a translation keeps verbatim (a product name, a URL, a number).
Saves land in the target's DRAFT and publishing stays the separate gesture it is everywhere (P7).
Two columns where there is room and stacked where there is not, because shrinking two panes onto a
phone gives a translator two unreadable columns instead of one readable one. It reads the row
(SH985's version guard needs that read, or the save is unconditional) and the group once — the
siblings list carries every document, so the source pane costs no request of its own. Reached from
the translations menu, and offered only on a page that *has* a source: on the source itself there is
nothing to put in the left pane.
**Per-field mark-reviewed writes a note** (`kind=reviewed`, SH1061's fourth kind), so review state
is a statement with an author and a time rather than a flag on a row, and an agent reads the same
marks; unmarking resolves the note, which is the gesture every other note already has. At narrow
widths the panes stack and a **toggle** picks which one is on screen, because two half-width columns
on a phone are two unreadable panes instead of one readable one. The rules the panes agree on —
the ordinal pairing, the one-field copy, the text rendering and which fields count as reviewed —
live in `lib/translate-pane.ts`, where `translate-pane.test.mjs` runs them.

**The Languages board is one site's coverage, at `content/site/:siteId/languages` (SH1060).** A row
per page and a column per language the site offers, reached from the site editor's Languages card —
which is the pair that makes sense: the card *decides* which languages the site is in, and the board
shows what it has in them. Each cell is a way in rather than a status: an existing translation
opens, and a **missing** one is created from the cell with `translate`, so the gesture happens where
the gap is instead of after walking to the source. The tallies come from the server's own pass over
the site (`GET /site/{id}/coverage`, SH1131) and never from the rows on screen — a board counting
what it displays would say "one missing" while paging through forty.

**A translation whose source moved is marked in the languages menu (SH1058).** `translationStale`
rides on the console post — absent unless true, so a monolingual instance's reads are unchanged —
and `PostTranslationsMenu` reads it twice over one call: an amber marker on the sibling rows, which
is how a curator sees *which* language needs a pass, and on the trigger itself, because the person
who has not opened the menu is the one who does not know the source moved under this page. The
translations list resolves the group's source **once** for the whole list rather than per row, since
every member shares one group by construction.

## The filters are the server's, and they travel (SH1022)

The content browser's toolbar has a **Filters** popover — post-type, state, changed-after,
changed-before, created-by, changed-by, include-subfolders — and every one of them goes into the
request. Nothing is filtered in the browser, which is the whole point: a client-side filter over a
paged listing narrows one page, and the rows on that page were chosen by a query that knew nothing
about the filter. That is the half-migration SH165 already paid for once with `sort`, and it reads
exactly like a correct answer until a folder outgrows a page.

`src/lib/content-query.ts` holds the words in one exported list rather than spread through JSX,
because they are a contract with three other surfaces — `/agent/find`, `shio_find` and `shio find`
spell them identically, and the server answers a word it does not serve with a 400 rather than
ignoring it. `test/content-query.test.mjs` reads `ShContentQuery.KEYS` out of the Java source and
fails in both directions: a filter the console sends and the server dropped would be a listing that
cannot load, and a filter the server gained and the console did not offer is the curator no longer
being able to ask what an agent can.

`filters` is part of the fetch's dependencies **and** of `queryKey`, so changing one re-queries from
page 0 and the spinner belongs to that filter's answer rather than to the previous one (SH703). The
badge on the trigger counts what is set, so a narrowed listing says it is narrowed — a folder that
looks empty because of a filter left on is the other way this fails quietly.

## Every list's state is in its URL (SH1023)

`useListState` (`src/hooks/use-list-state.ts`, over the pure functions in `src/lib/list-state.ts`)
holds a list's search, sort, filters and view mode in the query string. It replaces, per page, a
`useState` per control plus a debounced mirror of the text one plus a hand-joined `queryKey` — and
none of that was in the address, so the view on screen had no URL and a URL produced no particular
view. That is P5 broken where a person notices: a curator who narrows a folder to "drafts Ana
changed this week" had nothing to send anybody.

**The names are the server's** (`ShContentQuery`, SH1022), which is the whole point: the link a
curator pastes into a chat is the request an agent runs, and `shio find` prints the console URL for
its own result set. `sort` and `view` are the console's own — `view` because a grid and a list are
the same rows — and `site` on the search page carries the site's **name**, not its id, for the same
reason.

Three rules the hook enforces, each of which is a test:

- **A default is absent from the URL.** `?sort=order&view=list&type=` is the unfiltered view
  wearing a filtered view's address, and nobody can skim it.
- **A key the list does not own survives every write.** The updater form of `setSearchParams` is
  what makes that true under a debounce: closing over the params would resurrect whatever the URL
  held when the key was pressed.
- **The order is the declaration's**, so two callers that set the same filters in a different order
  produce one address. "Is this the link I sent you" has to be answerable by looking.

Text keys debounce and **replace**; everything else commits at once and **pushes**. Back walking a
search term one character at a time is Back being useless for what Back is for.

`test/list-state.test.mjs` is a census over `src/app/**/*.page.tsx`: every list route either drives
its state from the URL or carries the reason it has none. That is the deliverable rather than the
round-trip test — a rule like this is applied to four lists and forgotten on the fifth, which is
exactly how it started (`useSearchParams` was in three console files and one of them was a list).

## The browser pages its folders, and what that cost to make true (SH1025)

The content browser asks for `folderPage`/`folderSize` beside `page`/`size`. Folder paging is
opt-in on the server so the move/copy picker's whole-set read stays correct (see
[content-api](./content-api.md)); this list opts in, and **three behaviours that were correct
because the folders came back whole had to be paid for in the same change**:

- **Select → Folders** took what was on screen, which was all of them. It walks the pages now,
  bounded by `SELECT_ALL_CAP` and saying so when it stops short — SH237's rule, arriving at the
  other half of the listing.
- **The move/copy dialog's blocked set** was the folders among the rows on screen. A walked
  select-everything puts ids in the selection that no row shows, so it reads `knownFolderIds`, a
  ref filled at all three places folders arrive. A missed one is a folder offered as a destination
  for itself — the recursion SH203 refuses.
- **The folder sort** ran in the browser, which said something true about a complete set. It is
  `ShConsoleListingSort.folderSort()` now: a local sort over a page sorts one page and reads
  exactly like a correct answer, which is the half-migration SH165 named.

`loadMore` accumulates both halves — replacing the folder array would drop the rows already on
screen — and the page boundary names both, because a curator who cannot see their subfolder is
owed the sentence a curator who cannot see their post has had since SH165. `folderCount` in the
toolbar is `totalFolders`, not the page, or it says fifty for every folder over fifty.

None of the three fails loudly, which is why `test/folder-paging.test.mjs` asserts them rather
than trusting anybody to remember.

## The search page finds drafts, and says what it is not showing (SH1027)

`/bento/content/search` calls `/v2/content/search` now, not the delivery API. Three controls, all
in the URL (SH1023) and all spelled as the server spells them: the site by **name**, `state`
(empty meaning both — the default that makes a reviewer able to find an agent's drafts), and a
scope selector that is the two words `folder` and `subtree` wearing three labels — whole site,
this folder, this folder and below.

The page has no folder of its own, so the scoped options arrive from the content browser: its
toolbar carries a *Search in this folder* action that hands the search page a `folder:` **address**
built from the breadcrumb. A path rather than an id, because the vocabulary addresses content by
path (SH79) and an id there would be a fourth spelling of where something is.

**The root folder is not a segment (SH1184).** `/path` opens its breadcrumb at the site's root
folder and flags it `root`; `breadcrumbFrom` keeps that flag, and `searchHereFor` skips it, because
`ShAddressResolver.addressOf` stops below the root. On screen a root folder that repeats its site's
name is dropped — `createBlank` names it after the site, which drew `site/site/menu` — while a root
with a name of its own ("Home") stays as a step.

**The search route is passed, never defaulted (SH1187).** `searchHereFor` takes the route as a
required argument and the browser passes `ROUTES.SEARCH`. Its old default, a bare `/search`, is
not a route the router declares, so the button opened a 404.

**Back from a folder search returns to the folder (SH1188).** The toolbar navigates with
`searchBackState(location)` as router state, and the search page reads it once on mount with
`searchBackTo(location.state, ROUTES.HOME)`. Once, because a filter change rewrites the URL and the
router drops the state with it. Only an in-app path (`/`, not `//`) is accepted; anything else falls
back to the site list. Router state rather than a URL parameter keeps the search URL the shareable
question SH1023 made it.

Rows show their state, because a draft and what is live are now both in the answer, and the count
line carries `withheld` when the reader's ACL removed anything — "and 6 you cannot open" rather
than a shorter list with no explanation.

## The command palette answers with verbs and content (SH1028)

`⌘K` used to be handed the nav array and nothing else — seventeen destinations, and a placeholder
honest about it: *search areas*. Finding a post was open the palette, go to search, type the term
again.

**The verbs are compiled, not listed.** `bento-commands.ts` declares each command against a
surface id, and imports `console/surfaces.json` the way `bento-nav.ts` does — the backend's file,
so the palette's verbs cannot become a fourth list beside the rail, the surface declaration and
the agent's ops. A command naming a surface that is gone throws **at module load**, because a test
is not a guard.

**Nothing writes from the palette.** Every command carries `confirm`, and a command that writes
routes to the flow that asks — a publish plan, a form, a dry run. That is P7 at the one surface
where it is easiest to lose: the whole value of a palette is being quicker than navigating, and
quicker is exactly what turns a mis-typed Enter into a published page. The rule is a field rather
than a convention so `test/command-palette.test.mjs` can hold it, and it holds the select handler
too — a service call there would pass every other assertion.

**The product ranks, because the package will not.** `BentoCommandPalette` does not reorder a
product group (VDS123), which is right: the content half is ranked by a server that understands the
query. The actions half has no server, so `command-match.ts` ranks it — contextual first ("publish
this page" is a different offer on a post than in a folder listing), then match quality (a prefix
beats a substring beats a subsequence), then recency, worth at most one place so it can break a
tie and never beat a match. All of it a function of two strings, which is why it is asserted
without rendering anything.

**The content half is debounced and, more importantly, cancellable.** It calls the console's own
search (SH1027), so it finds drafts. A slow answer to `pri` arriving after a fast answer to
`pricing` would replace the right rows with stale ones and the reader would watch the palette go
backwards as they typed — a correctness problem, not a network one, so the token is checked on
arrival rather than the request aborted.

**One group, not the three the design asked for.** The package takes the nav array plus *one*
product group, so destinations stay where they were and actions and content share a group with
actions first. The ordering is intact — the component preserves what the product sends. What is
missing is the headings, and that is a package change.

## The media card says how many pages use the asset (SH1034)

`static-file.list.page.tsx` renders `usageCount` as a badge beside the file size. The detail dialog
has shown a count since SH62 and it answers one asset at a time, which is the wrong shape for the
question a curator opens this page with: *which of these can I clear out*. On the card the answer is
scannable.

Three things about it that are easy to get wrong:

- **`null` and `0` are different answers.** `null` means the response did not carry a count — an
  older instance, or a call that did not ask — and the badge is not rendered at all. `0` means
  nothing references the asset, and it *is* rendered, with `outline` rather than `secondary`,
  because zero is the value a deletion acts on. Hiding it as an absence would make the interesting
  case the invisible one.
- **The count comes from the page, not from the card.** The server stamps it on the rows it returns,
  in one indexed query; a card that fetched its own count would be one request per tile.
- **The pluralised strings are `usageBadge`/`usageBadge_zero` plus the two `usageHint` forms**, in
  `en` and `pt`. The zero case has its own word ("unused") rather than "used 0": a badge reading
  `used 0` is a sentence the eye has to finish before it means anything.

## Replace, and the widget that used to dead-end (SH1035)

Two console gaps behind one API that already worked.

**The detail dialog has a Replace control.** `static-file.list.page.tsx` calls
`POST /v2/staticfile/{id}/replace`, which has existed since SH210 with only a CLI caller. What is
rendered *around* the control is what makes a curator willing to press it: the count of pages that
will change (SH1034's usage, already loaded for the "Used in" list) **before** the file picker, and
the superseded bytes as numbered versions with a Restore beside each **after**. Without the second,
replace is a one-way door and a curator goes back to delete-and-re-upload — which mints a new id and
breaks every field pointing at the old asset.

Three details worth keeping:

- **The file input is cleared before the upload.** Choosing the same file twice in a row must fire,
  and an input that still holds it raises no `change`.
- **`onSaved` is called on both paths.** The size, the dimensions and the digest all moved, so a
  listing still showing the old ones is a card that disagrees with the bytes it links to.
- **The rendition list is reloaded after a revert, not filtered.** Reverting *captures* the bytes it
  replaced, so the list grew — and seeing it grow is how the curator learns that going back is itself
  undoable.

**The File widget opens a folder picker instead of refusing.** `file.widget.tsx` used to answer an
upload into an unconfigured field with `toast.error(t("content.noUploadFolder"))` and a return. The
folder setting is optional by design and is an administrator's job, so that told a curator mid-page
what was wrong and gave them nothing they could do from where they stood. Now the chosen file is
**held**, the content browser opens in a new `selectMode="folder"` (posts hidden — a row it could
select that is not a folder is a wrong answer the dialog offered), and answering finishes the upload
rather than restarting it.

The choice is remembered in a module-level `Map` keyed by **post-type attribute**, not globally: a
hero image and a press kit are two questions, and one answer for both files the second in the wrong
place. It is deliberately **not persisted** — the durable answer is the field's `widgetSettings`,
which an administrator sets once for everybody, and writing a session's guess to storage would let it
quietly become the configuration.

## One query layer, keyed on the address every other surface uses (SH1046)

The console had no query cache. Seventeen call sites fetched a whole collection with the same six
lines — a service at module scope, a `useState`, and an effect calling
`.query().then(setState).catch(() => setState([]))` — and the content browser's copy of it sat
**inside an effect keyed on the folder id**, so walking a tree cost one full post-type catalogue per
folder, for a list that decides a default and fills a filter and does not change while somebody
navigates.

`@tanstack/react-query` is the layer, mounted by `contexts/query.provider.tsx` in the place
`ShioServiceProvider` used to occupy. That provider is the story in miniature: a service-injection
context built for exactly this problem, wrapping the whole route tree, with **no call sites** —
every page constructed its own service anyway. The right instinct one layer too low. The services
are stateless wrappers over axios, so sharing the *instance* saves nothing; what every page was
re-doing was the request.

**A cache key is an address (P10).** `lib/query-keys.ts` turns SH79's grammar into the key:
`postType:` is the catalogue, `postType:Page` is one type, `folder:Cafeteria/Menu` is a folder. The
scheme is its own array segment, which is what makes `invalidateQueries(["postType:"])` reach the
catalogue *and* every single type under it — the whole invalidation story, with no second list of
what-else-to-drop to go stale. An address with no known scheme **throws where it is written**: a key
built from a typo would cache and never be invalidated, because every invalidation names a scheme,
and that is a screen showing yesterday's content with no error anywhere.

Three decisions live in `lib/query-client.ts`, which is a `.ts` file with no JSX so they can be
driven under plain Node — a behaviour reachable only through a rendered provider is one nothing
asserts:

- **30 s stale.** Long enough that a folder-to-folder walk costs one request rather than one per
  folder; short enough that a curator who edits a post type in another tab sees it on the next
  screen. A write invalidates its own scheme, so the window bounds staleness caused by *somebody
  else*, never by this session.
- **Retry once, never on a 4xx.** P6 says an error is an instruction, so a refusal is delivered,
  not repeated. A missing status is a request that never arrived, which is the case a retry is for.
- **The error handler is the cache's.** TanStack v5 removed `onError` from `useQuery` because a
  per-component handler fires once per observer — three screens sharing one failed read toasted
  three times. It goes through `reportProblem`, so a refusal arrives in the server's words (SH955).

**The error state was the quieter half of the win.** `catch(() => setState([]))` makes "the server
did not answer" and "there is nothing here" the same screen. Two pages had already worked around it
by hand, with a separate `error` state and a comment explaining that `undefined` means loading; the
other ten had not. `useQuery` answers the three apart by default.

`AdminListPage` takes an `address` prop and caches inside itself, which reached eight configuration
lists rather than the two on the task's list. `hooks/use-catalogue.ts` holds `usePostTypes`,
`useSites`, `useWidgets` and `useInvalidate`.

**What it cost, and where that is recorded.** 32,894 bytes raw / 10,054 gzip, measured by SH1043's
gate before and after; the ceilings in `console-size-budget.json` are raised with that sentence
beside them. Nothing else in the eager load moved.

Three instruments, because the claim has three parts and only one of them is checkable in the same
place. `test/query-cache.test.mjs` drives the real client with the real defaults in-process — two
reads issue one request, three concurrent callers issue one, invalidating a scheme drops its items
— **and sweeps `src/app` for a catalogue read whose answer is consumed inline**, so a page going
back to the old shape fails by name rather than by nobody noticing. `e2e/console-check.mjs` counts
the API requests of a second folder navigation in a browser, which is the only place the console's
own behaviour can be observed, with its shape held by `console-check-shape.test.mjs`. And
`test/eslint-suppressions.test.mjs` fails on a suppression for a file that does not exist — four
had survived two rounds of deletions, which is what a ledger nobody reads looks like.

## A read the console can hang up on (SH1047)

`AbortController` appeared nowhere in the console. The content browser rebuilds its page fetch on
every change of folder, sort or filter and left the request it replaced running, so a curator
clicking quickly down a tree could be shown a **slow earlier response over a fast later one**: the
rows of a folder they already left, under a breadcrumb saying otherwise. Nothing errors, nothing
spins, and there is no way to tell by looking — which is why the deliverable is an instrument and
not only a fix.

`lib/read-options.ts` holds both halves. `ShRead` is the one option every read takes, so sixty-two
reads across twenty-seven service classes cannot each invent their own spelling, and the lint can
ask one question. **Writes do not take it**, and that is a rule rather than an omission: aborting a
POST does not undo it, so a cancelled write is a write whose *answer* was discarded, which is worse
than waiting.

The other half is `isCancelled`, and without it this change would be a regression. An aborted
request **rejects**, so every `catch(() => setItems([]))` in the console would read a navigation as
a failed load and empty the list the curator is walking into. Three screens needed the guard
literally: the trash's three reads each fall back to `[]` so one failure cannot hide the other two —
a cancellation had to be rethrown past that fallback, or leaving the page mid-load emptied the trash
it was drawing.

Five screens were migrated, the ones the symptom names: the content browser, search, trash, the
media library and the activity trail. Each takes an `AbortController` in its effect and aborts on
cleanup. They did **not** move onto SH1046's query layer, and the reason is worth keeping: their
state is a cursor or an accumulating page, so caching them is a behaviour change (a curator
returning to the trash would meet yesterday's rows) that belongs to `useInfiniteQuery` and its own
task. Search gained the most from the move — its `let current = true` flag ignored the superseded
answer, where the signal stops the server being asked for it.

`test/fetch-cancellation.test.mjs` is the gate. Forty screens fetch inside an effect; five pass a
signal and **thirty-five are a declared list**, not a ceiling — a ceiling lets this task pay the
debt and the next spend it. The entries share one reason, written once rather than invented
thirty-five times, and the list's job is that a thirty-sixth cannot arrive quietly. It strips
comments first, which is load-bearing and not tidiness: every migrated file carries a paragraph
about cancellation, and `bento.root.page.tsx` says "an ambient signal" in its own prose — a raw scan
passed it for talking about the thing.

And `e2e/console-check.mjs` reproduces the race, because source can say an `AbortController` is
there and only a browser can say the superseded answer loses. It **holds the first folder's listing
open**, navigates past it, releases it, and reads the rows. A route pattern that stops matching is a
skip that says so rather than a pass: held zero requests and green is indistinguishable from a
console that is fine.

## Paying the uncancelled-reads list down, one slice at a time (SH1116)

SH1047 above migrated five screens and left the rest as a declared list. The list did its job — a
new one cannot arrive quietly — but **a gate that records a debt is not the thing that pays it**, and
the list stood at 33 when SH1116 opened. (§LXXII.21 and the roadmap line both say thirty-five; the
file's own count is the number.)

The task is explicitly in slices, because each screen's failure path is its own. **Slice one is the
five admin entity forms** — the API token, the auth provider, the exchange provider, the site webhook
and the tenant — and they are one slice because they shared a shape *and* a second defect:

**Each had `item.name` (or `item.title`) in its effect's dependency list.** So typing in the name
field re-read the row, and `setItem` put the saved copy back — reverting the curator's edits to
*every* field, a moment after they were made, racily, with nothing errored and nothing to notice by.
That is a straight P7 violation that had nothing to do with cancellation, and **the migration is what
fixed it**: a query's key is its subject, so the read re-runs when the tenant changes and never when
the form does. It is worth knowing that the two defects travel together, because the dependency array
is where an effect-shaped read keeps its mistakes.

These five went onto **SH1046's query layer** rather than taking an `AbortController`, which is the
opposite of what SH1047's five did and for the reason that note gives: these hold one entity keyed on
a route parameter, so caching is what you want, while a cursor or an accumulating page is a behaviour
change that belongs to `useInfiniteQuery`. The idiom is `queryFn: ({ signal }) => service.get(id, {
signal })` with `enabled: !isNew`, and two effects that only seed form state from `data` and report
`error` — `isCancelled` first, or the fix is a reliable error toast on every navigation in place of a
rare race.

The two provider pages gained a third query rather than a branch: the blank `model()` for a new
provider is a different subject from an existing one and is the same answer for every new provider, so
it caches under its own key. Their `vendors()` read stopped being component state entirely.

**What the browser probe actually said, which is not "green".** §LXXII.21 asks for the probe green
after each slice. On the instance this slice was driven against it is not, and it was not before
either: 16 of 57 probes fail, all on `missing: no hero heading` / `no frosted surface` over the bento
**admin list mosaics** and on fixture gaps (no CDA token, no editable props). That set is *unstable* —
across five runs of identical pre-slice code the failures moved between 11 and 16, and every single
route that differs after the slice (`bento-webhooks`, `bento-exchange-providers`, `graphql`,
`post-new:Page`, `legacy-console-post`, `bento-universal-editor`) had already failed in at least one
run before it. The probe reads `backdrop-filter` off computed style, which is timing-sensitive, and
this is the same instability that made `probe.ok` the wrong costing predicate for SH1113's budget.

So the evidence for this slice is the narrower and better-aimed one: **the five routes that render
the changed pages — `bento-token-new`, `bento-webhook-new`, `bento-tenant-new`,
`bento-auth-provider-new`, `bento-exchange-provider-new` — all pass**, and the list mosaics that fail
are pages this slice did not touch, failing on rendered chrome rather than on a read. A slice cannot
be blocked on a probe that is red for reasons it did not cause, and it must not be able to claim a
green one either.

**Slice two finished the admin area**: the user editor, the email configuration and the shared
group/role editor. Two of the three carried a dependency that was an *identity* rather than a
subject — `form.reset` on the user page, `service` on the identity editor. Both are stable in
practice, which is why nothing visibly went wrong, and **neither says which user or which group is
wanted**; a query key says exactly that. The email configuration is the one singleton here, so its key
carries no id and `emailConfig:` joined `SCHEMES` — a scheme rather than a bare string because
`addressKey` throws on an unknown one, and a key outside that list would cache under a name no
invalidation ever names. Its old `catch(() => setConfig(EMPTY))` was the worse half: it made "the
server did not answer" and "nothing is configured" the same screen, and the second invites a save
that overwrites the first one's real settings. The shared editor took a `scheme` prop rather than
deriving one from its route parameter, for the same reason. **The declared list: 33 → 28 → 25.**

**Slice three took the content editors** (SH1150): the folder, site and post-type editors. None of
the three had a failure path at all, so a folder or a site that would not load left the *previous*
one's fields on screen under a breadcrumb naming the new one. The site editor was the one worth
designing rather than translating: its single effect made three reads that are wanted together and
are **not one subject** — the site, its locale policy and the instance's locale catalogue have three
lifetimes, and the catalogue is the same answer for every site, so they are three queries.

**A render-phase seed may set this component's state, never a form (SH1166).** The post-type and
user editors moved `form.reset(loaded.data)` into the render phase alongside their own `useState`.
React allows a render to set its own state, but `reset` notifies every react-hook-form `Controller`,
which is a setState on *other* components. That raised "Cannot update a component (`Controller`) while
rendering a different component", and `console-check.mjs` failed three post-type routes on it. Both
editors now seed through `useForm({ values: loaded.data, resetOptions: { keepDirtyValues: true } })`.
react-hook-form resets from `values` in its own effect, only when the data differs, and keeps
in-progress edits. `test/render-phase-form-seed.test.mjs` scans for an `if` at component-body indent
whose block writes to a form. The user editor has no route in the check, so that site was found by
the scan.

**And a form seeded late starts from strings (SH1168).** The user editor had no `defaultValues`, so
its inputs rendered `undefined` until the user arrived, which React reports as an uncontrolled input
becoming controlled. `password`, which the server never returns, stayed `undefined` even after that.
`EMPTY_USER` gives every rendered field `""`, and `values` is the loaded user merged over it.
`test/user-form-defaults.test.mjs` holds the defaults to the `FormField` names the page renders, so a
field added later without a default fails there rather than in a browser.

**And one of the twenty-five was never a debt.** `post-type.new.page.tsx` held
`useEffect(() => {}, [t])` — an empty body — and sat on the declared list because the scan matches
`useEffect(` and a `service.` call *anywhere in the same file*, and its `create` is in the submit
handler. The fix was to delete dead code, not to migrate anything. That is worth recording because a
list that records a debt must be able to record that an entry was wrong, or it grows a floor nobody
can reach. **The declared list: 33 → 28 → 25 → 21.**

**Slice four found the instrument was wrong, and that was half the slice** (SH1151). The scan asked
two questions of the *whole file*: does it contain `useEffect(`, and does it contain a service call.
Neither is "it reads inside an effect", and both halves of that gap were real:

- **A write in a click handler counted.** `read-options.ts` offers cancellation to reads only —
  aborting a POST does not undo it — so the list was asking some files for something the design
  refuses. SH1150 had already met this as an empty effect standing beside a `service.create`.
- **Scoping to effect bodies alone over-corrected, and that direction is worse.** A read hoisted into
  a `useCallback` the effect calls becomes invisible, which the activity trail and the review queue
  both are. A gate that stops seeing a real uncancelled read fails *silently*; one that counts a
  write fails loudly. So the scan now reads the effects **and what they call**, one hop — not a call
  graph, because one hop is what the console does.

Recalibrated, it finds 37 fetching effects where the coarse version found 51, and the vacuity floor
moved with it. Eight declared entries were never debts.

**And the second finding is what the sharper scan left behind**: `BentoTenantSwitcher` and
`BentoTenantLinkNotice` each read `mine()` and `current()` in their own effect, and
`bento.root.page.tsx` mounts **both** — four requests for two answers on every visit to the bento
root, which is exactly the shape SH1113's per-route budget was built to see. They share one pair of
cached reads now, in `use-catalogue.ts` where a third caller will find them. **The declared list:
33 → 28 → 25 → 21 → 19.**

**Slice five took the post editor's three companions** (SH1152) — the note rail, the insights panel
and the translations menu — and turned up **a second shared read and a new class of defect**.

The shared read is SH1151's shape again: the translations menu reads the instance's locale catalogue
and one site's locale policy, and so does the site editor, and the menu is mounted from the post
editor. `useLocales()` and `useSiteLocalePolicy()` now serve both.

**The new class is what a cache brings that the reads did not have.** An effect-shaped read cannot be
stale — it runs, it answers, and the answer is as fresh as the render. A cached one is right when it
is fetched and **wrong after a write**. Creating a translation changes the group as seen from *every*
member of it, and the curator is navigated to the new page and may come back, so the write invalidates
the whole `translation:` scheme rather than the key it was called from; the note rail's
refresh-after-add is the same thing. This is the half of a migration easiest to leave out, because
nothing fails until somebody goes back — and it is worth carrying into every remaining slice.

The insights panel's key carries the **revision**, which is what makes its two long-standing wants
true at once: a save re-lints and a keystroke does not. As an effect dependency that was a re-run; as
part of the key it is what it actually is — a different subject per revision.

Four new schemes joined `SCHEMES` (`note:`, `translation:`, `insights:`, `locale:`) rather than
becoming suffixes under `summary:`, and the reason is invalidation: it matches on the key's first
segment, so folding them together would mean adding a note dropped the locale catalogue with it.
**The declared list: 33 → 28 → 25 → 21 → 19 → 16.**

**Slice six took the two widgets** (SH1153) — Content Select and File — and they are the clearest case
in the whole task. Both resolved a stored reference into a readable path behind `let alive = true`,
which is **the flag SH1047's own note names as the thing a signal replaces**: it *ignores* a superseded
answer where the signal stops the server being asked for it.

The cost a curator could see is the second one. The flag cannot tell a cancellation from a failure, so
both landed on `{ file: null }` — and that value is not nothing, it is the widget's
**missing-reference** state, the affordance it draws for "this points at nothing". So leaving a field
while its file resolved rendered exactly like a broken reference.

Each resolve makes **two** requests — the post, then its folder's path — so a signal threaded into only
the first cancels half the work and looks finished; the gate counts both. And the seeding guard is
`isSuccess` rather than a truthy check on the data, because `null` is a legitimate answer here: a
truthy guard would re-seed for ever on precisely the case the widget exists to show.
**The declared list: 33 → 28 → 25 → 21 → 19 → 16 → 14.**

**Slice seven took the two dialogs and paid off part of SH1136** (SH1154). The permissions dialog read
on mount and fell back to `[]`, and on a permissions dialog "the server did not answer" and "this
object has no rules of its own" are **opposite facts** — the second says the object inherits, which is
what a curator acts on.

The publish preview is the one worth reading. Its single effect did two jobs: it **read** the plan and
the route impact, and it **reset** four pieces of state so a second opening did not show the first
one's answers. Doing both synchronously is what earned that file one of SH1136's
`react-hooks/set-state-in-effect` suppressions — so **splitting them removed it**. The reads became a
query enabled only while the dialog is open, which means closing it *is* the reset for three of the
four; the two fields a curator types into reset on the open transition, during render. Running
`eslint --prune-suppressions` then found `post-insights.panel.tsx` stale as well, left over from
SH1152. Two of the seventeen, off both books — `eslint-suppressions.json` and the baseline that
declares them — because a slice that pays one has to take it off both or the next reader counts it
twice.

`publishPlan` is a **POST and takes the signal anyway**, which does not contradict `read-options.ts`'s
"writes do not take this". That rule is about a request that *changes* something: aborting it does not
undo it, so a cancelled write is a write whose answer was discarded. `dryRun=true` changes nothing —
it is a read wearing a write's verb, because the plan is computed by the writer — so hanging up on it
loses only an answer nobody wants. **The declared list: 33 → 28 → 25 → 21 → 19 → 16 → 14 → 12.**

**Slice eight took four more** (SH1155), and three of them carried something other than a missing
signal. `graphql.page.tsx` held `useEffect(() => {}, [t])` — the **second** empty effect on the list
after SH1150's — and its token list turns an empty state on the difference between `null` (still
loading) and `[]` (loaded, none usable), so a failure has to become `[]` rather than stay `null`.
`scheduled.page.tsx` is where the signal is worth most: its read walks up to **fifty pages** of
`/agent/find`, so a curator who opened the agenda and left immediately left that walk running to its
own bound. `working-line.tsx` keeps its `mark` as state on purpose, because a timer clears it when the
claim expires — the line goes away the moment it stops being true without asking again.

**And the setup page records the opposite lesson, which is worth having after seven slices of moving
reads out of effects: its redirect *belongs* in one.** Navigating is a side effect, and doing it during
render is what `react-compiler` refuses outright ("This value cannot be modified"). It reads nothing,
so this list has no business with it, and it sets no state, so `set-state-in-effect` has nothing to
say either. An effect is simply the right shape for a side effect — the rule was never "no effects".
**The declared list: 33 → 28 → 25 → 21 → 19 → 16 → 14 → 12 → 8.**

**Slice nine took the create form and one read out of the visual editor** (SH1156). The create form
repeated the post-type editor's mistake one page over — `t` in the dependency list — and had no failure
path, so a type that would not load left a form with **no fields**, which a curator would take for a
type that declares none.

The visual editor is the first file here where the answer is **derived rather than seeded**, and the
distinction is worth keeping: a form holds a local copy because the curator edits it, and nothing in the
editor is edited after it arrives, so the framed URL and the error are expressions over the query with
no second source of truth to disagree with the first.

**And it keeps its entry on the declared list**, which is the honest state: that site read is one of
eight this page makes — the post-type of a selection, the insertable catalogue, the patterns, a
section's editable props — and it is the heaviest file on the list. One read done is not the file done,
and a list that let a partial migration delete the line would stop describing the console. The
assertion says so explicitly, so the entry cannot quietly go later.
**The declared list: 33 → 28 → 25 → 21 → 19 → 16 → 14 → 12 → 8 → 7.**

**Slice ten closed the pattern this task set out to remove** (SH1157). The bento root's command palette
held a `queryToken` ref compared against itself in three callbacks, so a superseded answer was
**ignored** — the server was still asked for it and still answered. That is what SH1047's note names as
the thing a signal replaces, and a query key does the ignoring *for free*: an answer under a key nobody
is asking for is never rendered. Its debounce is a **committed term** now rather than a timer around a
request, which is also what makes "searching" honest — true while what was typed and what was asked
disagree, or while the request is in flight, and not merely because a keystroke happened.

The review queue's list is the most expensive read the task touched: `sessions()` scans 2000 audit rows
and resolves every post they touched, which is exactly why a separate cheap `pending()` exists for the
nav badge. Leaving that page mid-read saves real server work. Its `catch(() => setSessions([]))` made
"the queue could not be read" and "nothing is waiting for you" the same screen, and the second is the
one a curator stops checking. Its entry stays, because the deep-link detail read did not move.
**The declared list: 33 → 28 → 25 → 21 → 19 → 16 → 14 → 12 → 8 → 7 → 6.**

**Slice eleven took the two heaviest screens, and found that slice ten had shipped its report rather
than its behaviour** (SH1158). SH1157 wrote that "the queue could not be read" and "nothing is waiting
for you" must not be the same screen, deleted the `catch(() => setSessions([]))` that made them so, and
left a page where they still were: with the list on the query layer, `read.data ?? []` is `[]` on a
failure too, so `sessions.length === 0` went on drawing `review.blankTitle` for a queue nobody could
read. **Its assertion matched the mechanism** — that `catch` being absent — which is the same mistake
SH1028's guard made, and it is why the review queue now has a `queueFailure` derived from the query, a
third branch ahead of the two empty ones, and words of its own in both bundles. Three facts, three
screens.

The rest of the review queue is the more interesting half. Its run detail lived in a
`Record<string, ShReviewDetail | null>` that **four** call sites wrote to — the expander's click, the
deep link, a triage and a whole-run verb — and `null` in that map meant both "asked, not answered" and,
on a read that failed, "asked, never answering", which drew `common.loading` for ever with nothing to
press. A `ReviewRunDetail` component with its own query keyed on the run fixes all of it at once: the
signal is aborted when the row closes, two surfaces wanting the same run make one request, a decision
re-reads by saying the key is stale rather than by a second `setOpen`, and the failure is its own state
because a query has one. The map now holds only the curator's open/closed choice. The deep link (SH999)
consequently **reads nothing at all** — it expands a row, once per named run, from the router's
`useSearchParams` rather than from `window.location.search` — so this page has no effects left.

The post editor is the product's heaviest form, and the shape of its migration is the one worth copying
for a screen whose reads are not independent: both rows of one post go through **one** `queryFn` with
`Promise.allSettled`, because which of them is editable decides `state` while whether the PUBLISHED row
exists is the separate fact Unpublish needs (SH654) — `all` cannot report the second once the first has
answered, and the sequential try/catch it replaced could not either. The permission read stays a
*second* query on purpose: it answers a different question, and a failure means something different —
a permission read that broke leaves the buttons as they were, because the server is what decides and
hiding a control over a broken side call is the worse guess. `retry: false` on the load, because a 404
on a deleted post is an answer rather than a transient.
**The declared list: 33 → 28 → 25 → 21 → 19 → 16 → 14 → 12 → 8 → 7 → 6 → 4**, and the four left are the
visual editor's other seven reads and the three paged screens §LXXII.21 says want `useInfiniteQuery`
and a product decision.

**Slice twelve emptied the visual editor, and found the hazard one level lower than the pages**
(SH1159). Its three remaining effects were all doing the same thing by hand: a `loadedLabelsRef` so
selecting a second field of the same post refetches nothing, a `catalogue.key` compared against the
page the bridge reported, a `loadedSections.key` of `<page>|<length>`. Every one of those is a cache
key written out, every one *ignored* a superseded answer while the server was still asked for it, and
every one is a single line as a query key. The two derived answers — the field labels and the declared
fields — stopped being state at all, for SH1156's reason: nothing edits them, so a local copy could
only disagree. That also deleted the clear-on-navigation, because `setSelected(null)` makes the label
source `none` and a disabled query is empty by construction.

The catalogue was **one read of two subjects**, which is SH1150's finding on the site editor met
again: what a page accepts belongs to the page, and the patterns pushed to this instance are the same
answer for every page — so walking the frame through four pages asked for the pattern list four times.

The section array is the one with an optimistic write, and it kept its economy exactly: a remove or a
duplicate changes the length, so the answer is seeded under the key the echoed selection will carry
with `client.setQueryData`, and the client's 30s `staleTime` is what stops it being refetched the
moment the query is enabled again. The `!reordering` gate survives verbatim, because its reason
survives verbatim — the bridge echoes the new position *before* the patch resolves, so a read started
on that echo can land after the write and put the array back as it was.

**And the finding, which is about the services rather than the pages.** `insertable`, `patterns` and
`editable` each swallow a failure on purpose — an insert rail that cannot load must not take the panel
with it, and `editable` falls back to the PUBLISHED row where there is no draft. An aborted request
**rejects**, so without a rethrow each of those fallbacks reads a navigation as its own case: an empty
rail that never fills, or a second request for a row nobody is waiting for. §LXXII.21 states this
hazard about a page's `catch(() => setItems([]))`; the same hazard lives in every service fallback a
migrated page now cancels, and `isCancelled(error) && throw error` is the whole of the fix. Worth
looking for before the next slice, not after.
**The declared list: 33 → 28 → 25 → 21 → 19 → 16 → 14 → 12 → 8 → 7 → 6 → 4 → 3.** The three left are the
paged screens §LXXII.21 says want `useInfiniteQuery` **and** a product decision.

**The last slice emptied it, and took the product decision by not needing it.** The three were the post
editor's history panel, its live preview and the content picker dialog, and none went to the query
layer: the panel and the picker accumulate pages by hand, and the preview is a render the curator asks
for by pausing — no query key names that subject, and caching any of them is the behaviour change
§LXXII.21 said would have to be decided. They took an `AbortController` instead, the shape of SH1047's
five, but **one controller per component held in a ref**, not one per effect: each has two callers of
the same read (the mount, and a button — "show older", "load more", "refresh", a pause while live), and
a per-effect controller cancels only the first. Every read aborts the one before it, the effect's
cleanup aborts the last, and the spinner is lowered only by a read nobody superseded, because the one
that replaced it raised it a moment earlier.

Three things came with it. **The accumulating shape is the worse race**: an older page of the post the
curator left, landing late, is *appended* to the rows of the one they are on — real rows in the wrong
place. **The history panel said "nothing recorded" for a trail it could not read**, SH1158's finding on
one more surface, and has `activity.readFailed` now. And **the preview's render is a POST**, which
`read-options.ts` reserves signals away from — but it persists nothing, so it is a read in the only
sense that rule is about, and aborting it loses only a render a newer one superseded. The picker's
fallback to the site list is for a path that cannot be read, and a closed dialog no longer takes it.
`UNCANCELLED` is an empty declaration, asserted to stay empty.

**An empty list is only as good as the scan behind it (SH1140).** `FETCHES` needed a receiver whose
name ends in "service" followed by a dot, and `login-form.tsx` read in its mount effect both ways it
could not see: `new ShSetupService().status()` (a `()` between the class and the dot) and
`authorization.discovery()` (a service held under another name). The scan now accepts the inline
form and learns, per file, every variable assigned `new …Service()`. Widened first and run against
the tree, it named exactly one file and no false positive; the form then took one `AbortController`
for both reads, because each answer redirects the page and one landing after the curator had left
would pull them back to setup or to the identity provider. It is held in `MIGRATED`, and two planted
files keep both shapes named.

One thing the slices keep teaching, in a different spelling each time: the dependency array is where
an effect-shaped read keeps its mistakes. `item.name` in slice one, `form.reset` and `service` in
slice two, `t` and `form` in slice three — none of them is the subject being read, and a query key
cannot be anything else.

**Two gates slice one tripped, and neither was about cancellation.**

- **`react-hooks/set-state-in-effect`.** The natural shape for seeding a form from a query is
  `useEffect(() => { if (data) setItem(data) }, [data])`, and that rule forbids it — SH1136 is the open
  task for the ten suppressions of exactly this, so adding a suppression was not available and would
  have been the wrong answer anyway. These five set state **during render** instead, which is React's
  documented "adjust state when the input changes" pattern: a `seeded` value holds the subject already
  poured into the form (the row's id, or `"model"` for a new provider's blank) so each answer seeds
  once and a curator's edits are never overwritten. eslint is clean on it.
- **SH1043's headroom check, for a reason that is not this change.** Building the console from the tree
  under test put the eager total at 1810450 raw / 495034 gzip, leaving 3.70% and 3.69% against the 4%
  that gate asks for, so both ceilings were raised. This slice's own cost, measured by building HEAD
  and the change on the same tree, is **1.3 KB raw / 0.2 KB gzip** — the rest is accumulated growth
  that every previous green run had missed because `pnpm test` never builds the console and the gate
  measured whatever was sitting in `resources/public`. That is **SH1126** by name, and it is worth
  knowing before reading the budget's `raised` note: taking that entry for this feature's cost would be
  wrong by a factor of fifteen.

**Why the five are a second list in the gate and not five more `MIGRATED` entries.** `MIGRATED`
asserts a file still fetches inside an effect *and* passes a signal, which is right for an
`AbortController`. These no longer fetch in an effect at all. Asserting they do would be a sentence
that is no longer true **and that passes anyway**, because the scan sees a `useEffect` and a
`service.` call in one file and cannot tell they are unrelated — the gate's own failure message says
to say so rather than delete the line, so `QUERY_LAYER` is that, with its own three claims: the
`queryFn` takes the signal, `item.name` is not back in any dependency list, and the failure path asks
`isCancelled`. The declared list is asserted as a **ceiling of 28** that later slices lower and
nothing raises.

## A failure that stays in the screen it happened in (SH1049)

`main.tsx` mounted the design system's `ErrorBoundary` around the whole application, so a render
throw anywhere blanked the console — and this codebase's own prose records the bento shell coming
up blank behind exactly that, twice, with the compiler saying nothing. A blank page names nothing,
offers nothing, and leaves a reload as the only move.

Two sizes now, because a failure has a size. `RouteBoundary` wraps the shell's outlet: one screen
fails, the rail and the header and the palette stay, and a curator can go somewhere else. It is
**keyed on the pathname**, which is not decoration — a class boundary holds `hasError` until
something resets it, so without the key somebody who navigated away from a broken screen would
carry its error to every screen after, and the fix would look exactly like the defect.
`PanelBoundary` is the smaller one, for a section inside a form.

**Try again means re-run the reads.** Resetting the boundary alone re-renders the same components
against the same cached, failed queries, so the throw happens again and the button reads as broken.
SH1046's client is what makes the honest version possible: `resetQueries()` drops what the route
read, and the re-render asks again.

**The post editor was the worst case and is the named one.** It loads with `Promise.allSettled` on
purpose — SH654 needs to know which of DRAFT and PUBLISHED exist — so a 500 left `post` at `null`,
and `if (!post)` rendered a spinner with no end: no error, no retry, nothing to press. A slow
server, a deleted post and a broken instance were one screen. It records the rejection now and
hands it to `LoadProvider`, which is the package's own three-state gate — skeleton while the value
is undefined, blank slate with a way out once `error` is set.

Three instruments, and the split is the usual one. `test/loading-state.test.mjs` refuses the
*absence*: forty-two pages read from the server, thirty-eight model a pending or failed read, and
**four are a declared list** that shrinks by deletion. It follows a page that imports another
*page* one hop, because eight admin lists are three lines handing a fetcher to `AdminListPage` —
and only page to page, since the first draft credited `post-type.list.page.tsx` for importing a
header component that happens to have a `loading` prop. A hop that reaches any component reaches
something gated from almost anywhere, which is a scan that says yes to everything.

`e2e/console-check.mjs` answers **every** `/api/v2/` GET with a 500 on three routes and reads what
is on the page: a screen that says nothing a person can act on is the failure, by name. And
`console-check-shape.test.mjs` holds both, plus one thing worth more than either.

**The stripper is not a parser, and this is where that bit.** `stripComments` treats a slash
followed by two stars as a block-comment opener wherever it appears, including inside a template
literal — so the obvious Playwright pattern `` `**/api/v2/**` `` silently truncated the stripped
view of the script, and every assertion below it began matching an empty string and passing for it.
The length guard did not catch it: half a large file is still large. It happened **twice in one
hour** — once in the pattern, and once in the comment written to explain the pattern. Both probes
use a `(url) => url.pathname…` predicate now, and the shape test anchors on the script's final
statement, so a cut view fails instead of going quiet.

## One main, a skip link first, focus that follows the page, and spinners a reader can hear (SH1053)

The shell's half is VDS141, already in the installed design system 2026.3.12. It was measured live
before anything changed: every route has exactly one `<main>` (`BentoShell`'s; SH946 had already
removed the page-level ones, and the vocabulary harness keeps its own because it is mounted outside
the shell), the first Tab reaches "Skip to content", and a rail navigation leaves focus on the new
page's `h1`. `console-check.mjs` now holds all three. Every shell route fails on a `<main>` count
other than one, and `keyboard-landmarks` drives the first Tab and a rail click.

Shio's half was the spinners: 25 `<Loader2>` across 18 files, no `aria-busy` anywhere. The 15
inside a button became the design-system button's `loading` (VDS143). It draws the spinner, sets
`aria-busy` and `aria-disabled`, and keeps focus, where `disabled={saving}` dropped the focus of the
control just pressed. Any other condition stays on `disabled`, and the idle icon renders only while
not loading. The 10 standing in for content became `components/spinner.tsx`: the icon
`aria-hidden`, an `sr-only` label (the console's "Loading…", or none when visible words beside it
already say it), and `aria-busy` on the region it stands for. `test/spinner-a11y.test.mjs` fails on a
`Loader2` anywhere else.

## Axe against every console route, and what it found (SH1054)

`console-check.mjs` injects axe (`axe-core`'s own `source`, evaluated through Playwright, so a
page's CSP cannot block it) into every route after it settles, and into four routes at phone and
tablet widths. `scripts/a11y-debt.mjs` decides what a result means. Serious and critical always
fail. A lesser rule fails unless `a11y-debt.json` holds it for that route family, and a held entry
no run finds any more is reported for deletion. `a11y-debt.json`'s `vendor` names the one excluded
subtree, GraphiQL's own editor, with its reason, and every run prints it.

The first pass found 78 violations on 65 routes; the gate now holds zero serious or critical.
- The page hero's back link rendered with no text on 28 routes: `PageHero` passes a `backLabel`,
  defaulting to "Back".
- Icon-only controls had no names: the post-type rows, the admin save bars' back buttons, the
  `<TooltipTrigger><Info/>` hints, the field-row delete and drag handle, and six `SelectTrigger`s.
- `aria-required` sat on six `role="group"` wrappers (not allowed). The group's label already says
  "required".
- The HTML editor's `role="textbox"` and CodeMirror's surface were unnamed: `<label for>` cannot name
  a div, so both carry `aria-labelledby`.
- The content browser was a listbox whose options held three buttons each. It is a grid now: rows
  in `role="row"` with the controls in a `gridcell`, and SH1020's keys unchanged.
- **`<html lang="en">` over a Portuguese console.** `i18n/index.ts` stamps the resolved language.
- **Shio's `dark:` followed the OS, not the theme.** `index.css` declares
  `@custom-variant dark (&:is(.dark *))`. `:is` rather than `:where`, so a dark utility outranks the
  design system's same-layer base utility. `.dark .prose` also swaps the invert palette, which the
  compiled typography declares and never applies.

jsx-a11y's recommended rules run in `eslint.config.js`. 29 of 34 already passed. A post-type field
could be expanded only with a pointer, and its chevron is now a button with `aria-expanded`. Each
remaining exception is a pointer shortcut that duplicates a keyboard path, or autofocus inside a
dialog, and says so at its line.

## A field with a name, a state, and a way in from the keyboard (SH1052)

The post editor drew a `<Label>` with no `htmlFor` and then a widget whose props were `attr`,
`onChange` and `postId`. Nothing in that contract could carry an id, so clicking a label focused
nothing and a screen reader met each field as an **unlabelled edit box** — not one form and not one
post type: every field of every post, because this is the component every post is edited through.

Three smaller things went with it. The required marker was a bare `*`, which is punctuation and is
announced as nothing. The description was an orphan paragraph no control pointed at. And the File
widget's drop zone was a `div` with an `onClick` over an `<input type="file" className="hidden">` —
Tailwind's `hidden` is `display: none`, which removes an element from the accessibility tree *and*
from the focus order, so there was no keyboard path to attach a file at all.

**The fix is on the contract, because that is the only place the form can reach.** A widget cannot
invent an id the label already used, so `WidgetProps` gained `fieldId`, `describedBy`, `required`,
`invalid` and `labelledBy`, and `post-form-fields.tsx` mints them and hands them down.

**`fieldIds` is deterministic, and that is not a nicety.** `useId()` is React's answer and it mints
a value that differs between renders of different trees, so two renders of the same post would
differ in their markup — and a render digest (SH687) compares markup. A field name is already unique
within a post type, so the whole scheme is one function of it, in `lib/field-ids.ts` beside
`fieldAnchorId`. Three readers need it: the form that mints it, the insights panel that scrolls to
it (SH1013), and every widget.

**Two shapes, and the second is the contract's other half.** A widget with one control takes
`fieldId` and puts it on that control. A widget that renders several — a checkbox group, a code
editor with its own focusable surface, a set of relator rows — has no single element for `htmlFor`
to point at, so it takes `labelledBy` and puts it on a `role="group"`. Pointing a label at one of
several controls would focus an arbitrary one.

**The finding this turned up rather than the one it was filed for:** `blocks.widget.tsx` and
`relator.widget.tsx` declared their own `Props` with the three fields `WidgetProps` had. Widening
the contract widened everything except the two components that had restated it — the two most
complex widgets in the console. Both are `type Props = WidgetProps` now, and the lint holds it.

`test/field-accessibility.test.mjs` walks the widget map: every widget threads the id or names
itself, every widget with a control points at its own description, and **no form control is hidden
with `display: none`**. Each rule is a pure function of a widget's source and is driven against a
planted widget written the old way — every one of them passed on its first run, which is exactly
when a check has not been shown to check anything. What the scan does that a browser probe cannot
(SH1054 is that task) is fail on the widget nobody has written yet: the map takes a component, a new
one arrives with the old three props, and the defect returns for one post type with everything green.

Two existing rules had opinions and both were right. SH893 refuses `aria-required={required ||
undefined}` — a state attribute renders or vanishes depending on the framework's mood — so all of
them are `{x ? "true" : "false"}`, and `aria-required="false"` is a real statement rather than a
missing one. SH1010 held the hint paragraph's exact markup; the hint now carries an id the control
names, so the assertion holds the two halves instead of one line.

## The publish dialog can now act on the sentence it was already printing (SH1064)

SH996 put the routes on the publish preview: which URL goes live, which one **stops** resolving,
and how many published pages link to the one that goes. That paragraph was information with
nothing to do about it — a curator read "4 published pages link to /pricing" and pressed Publish
anyway, because the alternative was not renaming the page.

The dialog now offers a checkbox when `routes.stops` is set, **ticked**, and the tick sends
`redirect=true` to `POST /v2/post-unified/{id}/publish`. The default goes to the recoverable side:
a rename with no redirect breaks every bookmark into the page and nothing afterwards says so, while
a redirect nobody wanted is one post to delete. The server decides whether there is anything to
write — it compares the live row's `furl` with the draft's *before* publishing, because afterwards
both carry the new URL — so a stale tick on an ordinary publish writes nothing.

It **composes** rather than copying (SH1021's precedent one verb over): the controller builds a
`post.redirect` op and hands it to `ShAgentBatchService`, so whether a redirect is published, which
status it carries and what happens when a page already answers there each have exactly one home. If
the op is refused, the refusal says the publish already happened (`redirect-not-written`) — the
publish committed in its own transaction, and a curator told "this failed" about a page that is
live is the P7 surprise this exists to avoid.

### And the rename itself, which the console could not do at all (SH1132)

A post's friendly URL is its identity (SH81) and `post.move` on the agent surface was its **only**
writer: `PUT /post-unified/{id}` takes `data` and a state and nothing else, so the console's single
path to a URL change was a rollback that restores an older revision's. A curator who wanted
`/pricing` to become `/plans` had to ask an agent, or create a second post and delete the first —
the silently destructive workaround `post.move` exists to prevent, which takes the original's id,
its SH51 audit trail, its SH14 history and every inbound reference with it.

`POST /api/v2/post-unified/{id}/move` composes that op, the way the bulk verbs compose theirs
(SH1021). Three things it had to get right, and one the design left open:

- **Addressed by `post:`, never `id:`.** The batch refuses a bare `id:` for a *write* — a folder
  path and a friendly URL are separate namespaces, and guessing which was meant would create
  content in the wrong place (the SH79 rule in `agents.md`). The bulk verbs may use `id:` because
  publish, unpublish and delete address no destination; a move does, so it resolves the address
  where the id and the row are both to hand rather than asking the batch to relax a guard that is
  right. **The guard caught the first draft**, which is what it is for.
- **The destination goes in `to`, as an address** — the same field a folder move uses, because
  moving between folders and moving between URLs are one verb.
- **The redirect is offered, never assumed**, through SH1064's `ShConsoleRedirects` above. It reads
  the live row's URL as the destination, which after the move *is* the new address, so the
  composition is exact. Offered because a redirect is a published object at the old address and a
  page may be moving precisely so that nothing answers there.

**The open question, and why the answer was already written down.** §LXVIII.19 asked whether a
console rename should move the draft alone or both rows. `ShPostUnifiedWriteService.move`'s own note
answers it: moving only the draft would leave the live page at the old URL while every read — which
is draft-preferred — reported the new one, so *the post's identity would depend on who was asking*.
Both rows move. The consequence is real and is the surprise P7 forbids: **a rename of a published
page changes the live URL immediately, with no publish step.**

So it is not sprung. `dryRun` defaults to **true**, and the plan says the three things a curator
cannot otherwise know — that the page is live and the new address takes effect now, which address
stops answering, and how many pages link to it (SH140's scanner, the one `post.move` reports with,
because two counts of "who links here" would eventually disagree). `PostRenameDialog` renders that
plan and its confirm button does not exist until there is one; editing the URL drops the plan,
because a plan answers a question about one address and showing it beside another is worse than
showing none. A curator who reads it and presses Rename has not been surprised; one shown a text
field and a Save button has.

## The console lint runs in `pnpm test` (SH1075)

SH665 put `pnpm lint` in CI with the old findings in `eslint-suppressions.json`, so only new ones
fail. Nothing local ran it, and it stayed red in CI for weeks: twenty errors over seventeen files,
two of them on pages SH701 had paid off. `shio-react/test/lint-gate.test.mjs` now runs ESLint over
the console inside `pnpm test`, suppressions included, and fails on any error. It costs about
thirteen seconds.

Of the twenty, three were fixed rather than recorded. A disable comment named a `jsx-a11y` rule
that no config loads. `static-file.list.page.tsx` had its `setLoading(true)` back in the mount
effect, and its spinner is now the comparison SH703 used elsewhere: the page is loading while the
read it shows is for a different query, category or page. `review.list.page.tsx`'s deep-link
effect called `toggle`, which set state synchronously, so it now starts the read and sets `open`
when the read answers. The remaining seventeen are declared by name in
`cli/test/lint-baseline.test.mjs` against SH1136, the task that pays them off.

**SH1136 pays them in slices, and the first took three.** The history panel and the live preview,
both just reworked by SH1116, held a `set-state-in-effect` each and the preview a `refs`. The
panel's spinner is now **derived** (SH701's rule): loading while the rows on screen belong to
another object than the one asked for, or while an older page is in flight, so the read raises no
flag and a superseded read settles nothing. The preview's save render is **scheduled** on a
zero-delay timer, the way its typing render already was, so the flag is raised in a callback and
not the effect body; and `latest.current = data` moved from the render into a layout effect,
because a ref written during render belongs to a render React may discard. Both files joined
`PAID`. To see a file's findings without the baseline, run ESLint with `--suppressions-location`
pointing at a file holding `{}` (written without a BOM, or ESLint cannot parse it).

**The second slice emptied `react-hooks/refs`.** Two of the three were the same "latest value in a
ref" shape as the preview — `useChangeStream`'s handler and `useAutosave`'s `{attrs, save}` — and
got the same fix, the assignment moved into a layout effect: the reader in both is a callback that
runs after a commit, so it still sees the values on screen. The third was different in kind, and is
the one worth knowing: the Activity page kept its "newer" back-stack in a ref **and read its length
during render** to decide whether the button was enabled. That is state wearing a ref's name, and a
ref change never re-renders — so it is state now. The group stays declared, empty.

**The third slice finished it, and no two of the last five had the same fix** — which is SH701's
lesson again, that a count of findings is not a count of defects:

- **Languages** raised its spinner at the top of the read. It is derived now, from which
  `site|page` the board on screen answers; a refresh after asking the agent keeps the board up.
- **Search** reset four pieces of state in the effect when nothing was asked. It holds its answer
  as one value, and what it shows is derived from whether anything is being asked, so clearing
  the filters needs no effect at all.
- **The tenant-link notice** reset `dismissed` whenever the link changed. It records *which* link
  was dismissed, which is the rule itself written down rather than enforced by a re-render.
- **`useListState`** re-seeded its draft from the committed URL in an effect. It adjusts during
  render against `seededFrom`, React's pattern for state that follows an input.
- **The conflict dialog** reset its choice when `open` flipped, which is SH702's shape: its body
  now mounts inside `DialogContent`, so closing unmounts it and reopening starts from the stored
  values.

And the three non-component exports were **moved, not declared**: `conflictsBetween`/`readable`
to `lib/post-conflict.ts` and `constraintHint` to `lib/constraint-hint.ts`, pure functions that
read better as modules. `set-state-in-effect` and `refs` are both empty and stay declared; what
`only-export-components` still holds is the `ui/` and context API the baseline already names.

## The Activity page's live update, seen to arrive (SH1087)

`useChangeStream` (SH1005) had only source-scan assertions, and a stream that never delivers looks
exactly like a quiet instance. `e2e/change-stream.check.mjs` (`pnpm --filter shio-react
console:stream`) opens the Activity page against a running console and backend. It writes a site and
a folder through `POST /agent/batch`, then asserts two things. The "something changed" line appears
without a reload, and the rest of the page did not move while it appeared, because the page asks
before redrawing (P7).

First run, on a clean store on `:2710` with the Vite dev server on `:5173`: one stream request, and
the line appeared 809 ms after the write. Two things to know when running it. The console redirects
`/console/admin/activity` to `/bento/admin/activity`, and the bento shell renders a `section` with no
`main`, so the check reads the body. The line's full text is what gets stripped before comparing, in
both languages. What an open tab costs is SH967's `events.idle` budget: four statements per pass,
per tab.

## Design system 2026.3.12, and the waits it ended (SH1088, with SH963)

The console held `@viglet/viglet-design-system` at 2026.3.10 while npm served .11 and .12. It now
takes `^2026.3.12`. The release did two things at once:

- **It removed the chrome switch (VDS146).** `SectionCardChromeProvider`, `useSectionChrome` and
  `AdaptiveSectionCard`'s console branch are gone, so the root provider in `App.tsx` went with them.
  That was SH963, absorbed here because the console did not compile without it. `AdaptiveSectionCard`
  now renders the bento section with nothing above it. SH892's two structural rules are retired: a
  header-less card renders bento, and a nested `Content` keeps its body. `chrome-adapter.test.mjs`
  holds the new facts. SH957's census is an empty list asserted to stay empty, and the three guards
  that must name the switch to refuse it are listed in `REFUSES_THE_SWITCH`.
- **It ended eleven waits.** `design-system-waits.test.mjs` read each one by name. SH963 shipped, and
  SH943, SH946, SH947, SH964, SH966, SH968, SH988, SH1007, SH1024 and SH1026 left the list and lost
  their `DS VDS` dep. `PINNED` is 2026.3.12, and four waits remain: SH944, SH948, SH1051 and SH1053.
  The nine new bento exports are in the vocabulary harness's `DEFERRED`, each under the task that
  adopts it.

One reader had to learn something. SH891's key scan claimed "no false positives" over the bundle,
and 2026.3.11 broke it: `BentoActionsMenu` gives actions dotted ids (`entity.delete`), and a warning
quotes one inside a template string. `keysIn` now skips `id:` values and template-literal text.

## The design system's register of Shio, checked against Shio's source (SH943)

`consumers.json` in the design system is what its guards read to decide which consumers a change
reaches. Shio's entry once listed three of the seven entries the console imports. The package has
since corrected it and ships `viglet-ds-consumer-entries` (VDS129).
`shio-react/test/consumer-entries.test.mjs` runs that bin with `--consumer shio --check` from the
console package on every `pnpm test`. Today it reports seven entries over 216 files. A scratch tree
that imports one entry must fail, naming the missing six. The bin reports a disagreement on
stderr, so the test reads both streams.

## The reading column is the shell's (SH946)

`bento.root.page.tsx` renders the design system's `BentoShell` (VDS131) and hands it the rail and
the three header controls. The shell owns the rest: the rail's gutter, a sticky header, one
labelled `main` holding the reading column, a skip link, focus moved to the page's `h1` on
navigation, and the corner where back-to-top sits. The palette and the shortcuts dialog render
beside it, because the shell carries no state. `columnFor` in `app/system/bento-column.ts` picks
the column by route: `narrow` (56rem) for the three create and import forms, `default` (80rem)
everywhere else. Those are exactly the `max-w-4xl` and `max-w-7xl` those pages set by hand. No page
inside the shell sets `mx-auto max-w-*` or its own padding, and home and the post-type picker no
longer render a `main` of their own. The vocabulary harness is mounted outside the shell and keeps
its layout.

**A tool that owns the viewport takes the `full` column (SH1169).** The GraphQL explorer sized itself
as `100vh - 4rem`, SH908's premise about the chrome above it. The shell later put a 61px header and
40px of `main` padding on each side, and the editor ran 21px past a 1000px window, with the document
scrolling. `columnFor` has a `FULL` table now, holding `/bento/graphql`. The shell's `full` column
makes `main` a flex column that fills what the header leaves, with no bottom padding, and the
explorer is a `flex-1 min-h-0` child of it. Measured at 1600x1000: the editor ends at 984px and the
document does not scroll. `bento-column.test.mjs` reads the two tables separately and forbids a
viewport height in `graphql.page.tsx`.

Proof, as the design asked. The surface digest before and after the page edits is byte-identical
over 48 surfaces. Probed against a running console: one `main` per page, the width the column
names (1280px default, 896px narrow), the skip link present, and SH1087's delivery check still
green. `bento-column.test.mjs` holds the rule, the root's composition, and that no page sets a
width.

## The page lint runs in `pnpm test` (SH947)

`shio-react/test/page-lint.test.mjs` spawns the design system's `viglet-ds-page-lint` (VDS132),
resolved through the installed package, over `src` with `--check`. It enforces the contract's two
named rules: `page.column` (a routed page's outermost element sets no `max-w-*`, gutter `p*-`
or rhythm `py`/`mt`/`mb` class) and `primary.direct` (nothing writes `--vg-primary`). A
planted `max-w-4xl` on a copy of `trash.page.tsx` must turn it red naming the file.

Its first run found 29 findings that SH946's own check could not see, because it looked only for
`mx-auto max-w-*`: a bare `p-6` inside the gutter the shell already draws. Twenty-seven were
removed. The six infrastructure editors that set `max-w-2xl` (tokens, auth and exchange
providers, webhooks, tenants, email config) now read `narrow` from `columnFor`. Two pages carry
`viglet-ds-allow-page-column -- <reason>` because they render outside the shell: the vocabulary
harness, and `not-found`, which also serves the bare `*` route. A page wanting another width names
it in `bento-column.ts`, never on its own element.

## The assistant dock is a status surface (SH964)

`app/system/bento-status-dock.ts` renders the package's `VigletAssistant` from
`useAssistantNotifications`, and `bento.root.page.tsx` passes it to `BentoShell` as `dock`. The
hook reads the live toast list rather than patching `toast`, so the console's existing calls
(every one imports the package's `toast`) move the mascot and become the caption with no edits.
There is no `onSend`, so there is no composer: Shio hosts no model. The caption is a live region
and the mascot is `aria-hidden`. The labels come from the package's own `assistant` namespace,
which `registerVigTranslations` already loads.

It is `.ts` with `createElement` so `bento-status-dock.test.mjs` can mount it in jsdom and drive
it with the real `toast`. `renderToStaticMarkup` runs no effects and would prove nothing here. The
test asserts idle, then `error` with the toast's title, then idle once the toast is dismissed. It
reads the state from the canvas's `data-state`. The corner was probed in a browser at 1400px and
390px, with and without reduced motion: back-to-top stacks above the dock, the two boxes do not
intersect, and the page logs no errors.

The dock costs the eager load 14.6 KB raw and 5.3 KB gzip, all of it in the design system's
`index.es.js` chunk, and `console-size-budget.json` records the raise. Importing it with `lazy()`
was measured and saved nothing: the bundler keeps the package's whole ES entry in one shared
eager chunk.

## Every write says how it ended (SH965)

The dock (SH964) hears only toasts, so a write that resolves or fails without one is invisible.
`shio-react/test/support/write-outcome.mjs` reads the TypeScript AST. It treats a service method
as a write when its body calls `axios.post|put|patch|delete`. Outside `src/services`, it finds every
call to a write through a receiver named like a service, and climbs to the handler. It skips
inline callbacks (`.then`, `.map`, `Promise.all`) but stops at a hook's argument. Success means a
toast outside any catch. Failure means a toast inside a `catch` clause, a `.catch(…)` argument or
an `onError`. Any `src` function whose own body toasts counts, one level deep, so `reportProblem`
qualifies. Handlers passed to `BentoEntityShell` as `onUpdate` or `onDelete` count for both
paths, because the shell toasts `forms.common.updated`/`notUpdated` and `deleted`/`notDeleted`
itself.

`write-outcome.test.mjs` holds the silent ones as two named lists. EXCEPTIONS covers dry runs,
preview mints, autosave, the tenant switch and setup, each with a reason and what would falsify
it. The test prints both counts, fails on a new silent write, and fails on a held entry that is no
longer silent, so the lists only shrink. A new write handler needs a success toast and a catch that
calls `reportProblem(error, t("common.error"))`.

### WORKLIST is empty, and what its sixteen were (SH1139)

**Five said nothing at all.** The token, auth provider, exchange provider, webhook and tenant
editors awaited a delete and navigated away — so the row vanished from the list whether the server
agreed or not, a refusal escaped as an unhandled rejection, and the outcome was indistinguishable
from a button that worked. They toast `common.deleted` and catch.

**Nine toasted success and let the failure escape**: the user editor's create and update, the
group/role create, folder create, post-type create, edit and list delete, site create and site
import. A refused create there looked exactly like a button that did nothing. Each gets the catch
the rest of the console already had.

**The two static-file deletes were the worst, and are the ones worth remembering.** Both swallowed
every failure with `.catch(() => {})`, removed the rows from local state, and *then* toasted
success — so a file the server refused looked deleted until a reload put it back, and a bulk pass in
which **nothing** went reported all of it deleted. The single delete now moves state only on
success. The bulk one goes through `deleteEach` (`lib/batch-delete.ts`), which **SH953 wrote for
this exact shape** in the content browser: refusals are counted and named with `refusalSummary`,
only what went leaves the list, and a row that refused stays selected so the reason can be read and
the delete retried. Reusing it rather than writing the counting a second time is the whole reason it
lives in `lib/`.

**One thing the lint had to learn, and the alternative is why.** `deleteEach` catches internally and
hands the caller its refusals, so a handler using it has no `catch` of its own — and the reader
called the bulk delete silent. The other way out was to move the delete behind a local function the
reader cannot see through, which is how the content browser's equivalent site is invisible to this
lint: that passes by **hiding from the instrument**, which is worse than the defect. So `paths()`
takes a `deleteEach` call as a failure report, on the same grounds it already accepts
`BentoEntityShell` — a helper that owns the reporting contract is not a silent write. The exemption
is deliberately narrow: calling the helper is not enough, the handler must say something about
`refusals`, because a caller that took the outcome and toasted only `deleted` would be the original
swallow with more ceremony. A planted fixture holds both directions.

**Closed with a list rather than a count.** Emptying WORKLIST would make the general rule cover
these by default, and that is the weaker claim — it passes just as well if one of the sixteen is
renamed, deleted, or has its write moved somewhere the reader cannot follow. `SH1139: every site the
worklist held reports both outcomes, and still exists` names all sixteen, so a regression says
*which* outcome went missing from *which* screen.

## A refusal is a dock report (SH966)

`src/lib/dock-reports.ts` is a module-level store: `pushReport`, `markReportRead`,
`dismissReport` and `useDockReports`. It keeps the newest ten VDS134 `report` messages. It is a
store rather than a context because a page reports and the dock renders in different subtrees,
and a store has no provider to forget. `BentoStatusDock` hands the reports to `VigletAssistant`
as `messages`. While a toast is on screen the toast drives state and caption. Otherwise both are
left to the dock, which follows its newest unread report and goes idle when none remain.

`reportRefusal(error, fallback, { t, goToField, applySuggestion })` in `lib/problem.ts`
replaces `reportProblem` wherever a page can act on the refusal. It raises no toast. The report
text is the headline plus the advice (fix, didYouMean, allowed). Its actions are "Go to <field>"
for `at`, and "Use "<didYouMean>"", which writes the suggestion into that field. A refusal with no
problem document still reports, as `common.refusalStatus` with its HTTP status. The allowed list
is part of the text rather than a third action, since the console has no surface to open for it.
The post editor is the first consumer: save, conflict resolution, publish and unpublish. The SH965
lint counts `pushReport` as a report, so a handler that reaches it is not silent.

## Agent runs report in the dock (SH968)

The shell calls `useAgentRunReports` (`app/system/bento-agent-runs.ts`). It reads SH967's stream
through `subscribeChanges`, which now shares one `EventSource` per scope across every caller, because
each open stream is a subscription the server polls every second. `lib/agent-runs.ts` is the pure
tracker: a run starts at the first write this screen sees carrying an `agent` session, each write
bumps the dock's activity, and it ends after `RUN_QUIET_MS` (20 s) of quiet, since an agent never
says it is done. A curator's own writes carry no `agent` and are ignored. While a run writes, the
dock is `working` with "<agent> is working on <site>". At the end it gets a report: `attention`
when the run left undecided writes, `success` otherwise, with "Open in review" (the run, via
`?session=`) and "See the activity" (`?who=agent`). The pending count is read once at load and
becomes one report when it is not zero. The header count and its provider are gone.

The stream had been dropping the fields this needs. `ShEventStreamHub.withoutId` rebuilt each
row through the pre-SH999 constructor, so `agent`, `agentName` and `review` never reached a
subscriber. `ShEventStreamTest.sh968TheStreamKeepsTheAgentThatWrote` fails without the fix. A live
probe checked the whole chain: one stream connection shared between the Activity page and the
shell, no header count, a batch sent with `Mcp-Session-Id` turning the mascot to working, and an
attention report with its review action after the quiet period.

## Schedule outcomes report in the dock (SH969)

The feed half (`ShScheduleOutcomeFeedTest`) put the scheduler's outcomes on the feed. A transition
that went through is a `PUBLISH`/`UNPUBLISH` row by `scheduler`. A failure is a `schedule`
incident with the message, `id:<post>` and a `retry` batch op. `lib/schedule-reports.ts` maps a
page of that feed to reports. A failure is `error` with the message and fix, "Open the page" and
"Retry now". A success is `success`, naming the page and its time. `useScheduleReports` in
`app/system/bento-schedule-reports.ts` feeds it from the shared stream. Retry goes through
`ShAgentFeedService.retry`, which posts the incident's own op to `/v2/agent/batch` (P10), toasts
on success and `reportProblem`s on failure. At load, `replayChanges` opens a separate stream with
`?since=` this browser's last cursor (`shio.dock.feedSeen` in localStorage, a week back on a first
visit), reports the failures in that range, and closes after 3 s of quiet. It goes through the
stream and not `GET /v2/agent/changes`, because the hub applies the reader's content ACL to
incidents and the agent feed does not. It is not the shared connection, because the Activity line
and the run tracker would treat a week of replayed rows as happening now. A lost cursor re-reports a week of failures and never misses
one. `schedule-reports.test.mjs` plants a failure and presses retry, and the Java test proves the
op publishes exactly once.

## Form submissions report in the dock (SH970)

The feed half (`ShFormSubmissionAPITest`) gave a form submission's `CREATE` row a
`submission: { title, summary }`, and a tripped honeypot writes nothing, so it never reaches the
feed. `lib/arrival-reports.ts` picks those rows out of a stream page. One arrival is an
`attention` report with the title and summary as its caption, "Open the submission" and "Move to
trash". Several gathered within `ARRIVAL_GATHER_MS` (2 s) of each other become one report naming
the count and the newest three, with only "Open the newest", because one click in the corner must
not discard a campaign's leads (P7). It says "trash" rather than "archive" because a submission is
a post, and a post has no archive state; an inbox is Block BI's. The stream withholds ids, so the
actions in `app/system/submission-actions.ts` call `ShAgentFeedService.locate` first
(`GET /v2/agent/resolve`, classified in `ShConsoleReadAclLintTest`). It is named `locate` because
the SH965 lint keys writes by method name, and `resolve` is a POST on the note service.

## What the dock reports is the curator's choice (SH971)

`ShUser.dockPreferences` (changeset `sh971-user-dock-preferences`, VARCHAR(1024), nullable) holds
`{"muted": [...]}`. It sits on the user rather than per tenant, so the choice follows the curator
across devices, the way `lastPostType` does. `GET`/`PUT /api/v2/user/current/dock`
(`ShDockPreferencesAPI`) reads and replaces it whole. The kinds are a closed set, `saves`,
`agent-runs`, `schedules` and `arrivals`, and anything else is a teaching 400 with the set, the
nearest match and `at`. The stored list is sorted and deduplicated (P5). A refusal is not a kind,
because it is always a failure. `surfaces.json`'s `consoleOnly` already classified this as a
`preference`.

In the console, `useDockPreferencesLoad` reads the choice at shell mount and `setMutedKinds` applies
it. Every `pushReport` carries its kind, and a muted kind's report is dropped unless its tone is
`error`: a failure always reaches the dock, and muting never hides the record. With `saves` muted,
only a failing toast moves the mascot. With `agent-runs` muted, a run shows no working state and no
bumps. The header's `BentoDockPreferences` button opens one switch per kind, and the dialog says
that failures always arrive.

**The mascot can be kept still.** The same document carries `still` (the record's second
component; a document stored before it existed reads as `false`, which is what the dock did then),
and no changeset was needed because the column holds JSON. `setMascotStill` applies it, and
`BentoStatusDock` passes it as `paused`, which `VigletAssistant` forwards to its avatar since design
system 2026.3.13 (VDS171, filed from here). Only the motion stops. A paused dock sets its caption
whole instead of typing it, and the live region announces exactly as before. That whole caption is
also what `bento-status-dock.test.mjs` reads, because the package marks a paused dock in no
attribute. The switch is the dialog's last row, **Animate the mascot**, on by default.

The same release deepened the accent label to orange-800 (`src/index.css`): VDS179 measures it on
the accent's own tints, where orange-700 read 4.49:1.

## The Universal Editor docks the assistant itself (SH972)

SH907 put the visual editor outside the shell, so the shell's corner and dock are not there.
`visual-editor.page.tsx` mounts `<BentoStatusDock inline />` in flow at the foot of its properties
panel. The `aside` is now a flex column: its content scrolls above, and the dock takes the panel's
own height below. It is not in the toolbar because the collapsed orb is 132 px and inline mode keeps
that size. It is not pinned over the preview because it would cover the page being edited. The
collapsed caption still extends to the left of the orb as `pointer-events-none` text, over the
preview's edge, while it types.

Saves drive it. `persist` sets `working` ("Saving your changes") while patches are in flight, clears
it in `finally`, toasts the saved field names on success, and routes a refusal through
`reportRefusal`, so the dock gets the server's problem document rather than the word Error. A
browser probe on a fresh instance measured the preview iframe at 1046×809 with and without the
dock, found the dock inside the panel, and logged no page errors.

## The mascot is the one notice (SH1182)

While a dock is mounted the toast region is hidden, so an outcome is shown once, by the mascot.
`registerDock` in `lib/dock-reports.ts` counts mounted docks (`BentoStatusDock` registers in an
effect) and `useDockMounted` reads the count. It is a count because the shell and the visual editor
each mount one. `ConsoleToaster` in `App.tsx` wraps `<Toaster />` in a `<div hidden={docked}>`.

Hidden, never unmounted. `useAssistantNotifications` reads sonner's store, but the mounted
`Toaster` runs each toast's timer and dismisses it, so without it no toast leaves the list and the
mascot stays on its last state. The wrapper is always rendered, because a conditional wrapper
remounts the region and drops the toast on screen. Login and setup mount no dock and keep visible
toasts. `toast(...)` stays the way a page reports an outcome: nothing calling it changes. With
`saves` muted, a successful save is now silent, since the toast that used to carry it is hidden.
A browser locator for an outcome's text matches the hidden toast first, so it needs
`.filter({ visible: true })` (the Universal Editor drive in `e2e/console-check.mjs` does).

## One diff, answered by the server (SH988, first half)

`GET /api/v2/post-unified/{id}/diff?from=&to=` (`ShPostDiffAPI` / `ShPostDiffService`,
`requireRead`) answers `{from, to, before, after, fields: [{name, changed}]}`. A side is `draft`,
`published` or `rev:<n>`, and the defaults are `published` → `draft`. `published` on a page never
published is `null`, which is a create and not an error. Fields are the union of both sides' keys in
name order, and a field is changed when its JSON differs. A bad side is a teaching 400
(`unknown-diff-side`), and a missing revision is SH987's `no-such-revision`.

The console hands the answer to the design system through `lib/version-diff.ts`: `diffFields`
labels from the schema and keeps undeclared names, `kindOf` maps HTML widgets to `rich` and text
to `text`, and `railVersions` lists the draft, the published page, then each revision newest
first, with agent revisions marked. `PostVersionsDialog` is now `BentoVersionRail` plus
`BentoDiff` over any two picks, with a restore of the older side behind a second press (the
published page, or `POST …/revisions/{n}/restore` with the draft's If-Match). The review queue
offers `ReviewEntryDiff` inline for every entry that still exists, so a create has one. An approved
entry names the fields its audit row recorded (`ShReviewDetail.Entry.fields`, SH989's column)
instead. A browser probe read a created page as all additions and an edit as a word-level change
with the unchanged field collapsed.

**Second half.** A create's diff (`before` null) opens with `CreatedPagePanel`: the render digest
from `/agent/render` (words, headings, links and images, plus the outline) and a preview button
that mints a token. It appears in the dialog and in the review queue's inline diff. When the newer
side is the draft, each changed field gets a "Restore <field>" button. It patches just that field's
older value into the draft, behind a second press and the draft's If-Match. A browser probe restored
Title and left Body's change standing.

`shio diff` still composes over the agent read surface (SH619, P10) rather than calling this
id-keyed console endpoint. What keeps the two from disagreeing is
`shio-app/src/test/resources/diff/agreement.json`, one set of cases that both
`ShPostDiffAgreementTest` and `cli/test/diff-agreement.test.mjs` must answer alike. Its first run
found a real difference: the CLI's `same()` compared `JSON.stringify` output, so an object whose
keys only moved read as a change. It now canonicalizes object keys, keeping list order, the way
`JsonNode.equals` does.

## The dock asks the connected agent (SH1006)

Shio hosts no model, so a question typed into the dock is answered by the agent already driving
the instance, over notes. `dock-chat-send.ts` writes a `request` note on the open post or folder.
The agent reads open requests from the MCP resource `shio://requests` (`ShNoteRelay.render`, REST
mirror `GET /v2/note/open?kind=request`), answers with `note.add` `{kind: reply, parent: <note>}` on
`id:<object>`, and resolves the request. `lib/dock-chat.ts` keeps this session's questions and their
replies. `BentoStatusDock` merges them with its reports by time, sets `busy` while one waits, and
counts an unseen reply as unread. Opening the dock marks replies seen.

The composer (`onSend`) is offered only when `GET /v2/note/relay` says an agent run started on
this tenant within `ShNoteRelay.WINDOW` (8 h, from `sh_agent_run.started_at`) and a post or folder
is open. Without an anchor there is nothing for the note to hang on, so the dock stays a status
surface rather than guessing a site. Replies are found by re-reading a waiting request's thread
every `REPLY_POLL_MS` (5 s), and only while one waits. Notes are not on the change feed, so SH967's
stream never carries a reply, and bounding a poll to the seconds a curator waits costs less than
putting notes on every subscriber's stream. `ShNoteRelayTest` drives the round trip through the
batch that `shio_write` runs.

## An answer can carry its change (SH1007)

A reply note already names the run that wrote it (`ShNote.agentSessionId`, SH106, filled by
`note.add` from the session), so the agent's "one field" is the session it is already in: draft
the change, then reply, in the same run. `runOf` in `lib/dock-chat.ts` reads it and ignores a
`console:` session. When a reply names a run, the relay pushes a report with two actions: "Open
the change" (the review queue at `?session=<run>`) and "Approve and publish"
(`approveFromDock`, the queue's own `POST /review/sessions/{run}/decisions` with `APPROVE` for the
anchored page, so the queue's permissions apply and the dock is never a way around review).
Rejecting needs the note a rejection must carry, so it happens in the queue, which the first
action opens. A chat message holds at most one action in the design system, which is why the actions
live on a report beside it. `ShNoteRelayTest.aReplyCarriesItsRunAndApprovingItPublishes` drafts in
a run, replies, approves, and reads the published row.

## Ask the agent from a field (SH1019)

`PostFormFields` shows "Ask the agent" on text-like fields (`ASKABLE`: Text, Text Area, HTML
Editor) when the editor passes `onAsk`, which it does only while `useAgentAvailable()` says a run
is recent. `AskAgentDialog` opens a short prompt prefilled from the field's label and sends a
`request` note anchored to the post and that `fieldPath`. When the reply to a field request lands,
the relay pushes a report whose action "Apply to <field>" calls `requestApply` in
`lib/field-suggestions.ts` and navigates to the post. `useAppliedSuggestions` takes the value once
the post has loaded (earlier, the load would overwrite it) and writes it into the form as an
unsaved edit, so dirty state, validation, autosave and save treat it as typed. Nothing on this path
writes to the server. For a field request the reply's body is the value, so there is no separate
suggestion field on the note and no schema change.

## The filter bar over the list's URL (SH1024, content browser)

The content browser draws the design system's `BentoFilterBar` over the same words its URL already
holds (SH1022's vocabulary, SH1023's `useListState`). `lib/filter-bar.ts` is the only translation:
`toBarValue` reads `q`, `type`, `state`, `locale` and the `modifiedSince`/`modifiedBefore` range
into the bar. `fromBarValue` writes every one of those keys back, empty when cleared, so a removed
chip leaves the address. `contentFacets` offers only values that exist: this instance's post types,
and a language facet only when the listed rows carry more than one locale. The bar replaced the
magnifier toggle and the popover's type, state and date fields. The popover ("More filters") keeps
author, modifier and subtree, and its badge ignores what the bar shows as chips. SH1020's `/`
focuses the bar's text field.

**The bar's value and handler must be stable.** It debounces typing in an effect keyed on `value`
and `onChange`, so a fresh object per render restarts the timer and the query is never sent. That
showed up only in a browser, where the page re-renders faster than the 300 ms pause. `barValue` is
memoized on the six primitives and `onBarChange` is a `useCallback` over `list.patch`, and
`filter-bar.test.mjs` pins both. A probe typed "pricing" (`?q=pricing`, two rows of three), picked
state draft (`&state=draft`), and reloaded into the same rows, field and chip.

**Search draws the same bar.** `search.page.tsx` holds the whole vocabulary in its URL (`q`,
`site`, `type`, `state`, `locale`, the modified range, and the `folder`/`subtree` scope), and the
bar replaced its text box, site select, state select and submit button. `site` is the one key only
search offers: `withSiteFacet` puts it first, by **name** (the word `/agent/find` takes), and only
when the instance has more than one site. It lives in `BarKey` anyway, because a list's `patch`
drops a key its defaults do not declare, so the browser never sees it. The locale facet is built
from the rows found. A filter alone is now a question ("every draft on Blog"): the search runs when
any asking key is set, not only on a typed term. `q` is not a hook text key there, because the bar
debounces its own field. The scope stays a select beside the bar, shown only when the URL brings a
folder, since no chip can choose a folder.

**Trash draws it too, and filters locally.** The trash is one bounded list of three kinds (sites,
folders, posts) read whole, and only the post read speaks the vocabulary, so a server-side `q` would
filter the posts and leave every deleted folder and site on screen. So `q` and `type` live in its URL
(its census entry in `list-state.test.mjs` is now `null`) and `matchesLocally` applies them to the
rows already held: `q` as a title substring, and `type` as the post-type name, which a folder or a
site does not have. The type facet offers only the types in the trash, and appears only when there
are two. A filter that matches nothing says so, not "the trash is empty".

**The review queue filters its runs, locally.** A run is not a post, so the post vocabulary's
server filters do not reach it, and the queue is one bounded read. Its URL holds `q` and `site`
(census entry now `null`). `matchesRun` reads `q` against what a curator knows a run by: its
premise, the agent's name, who ran it, and the session id, since a run with no premise still has
that. `site` keeps runs whose `bySite` names it. `runSites` builds the facet from the listed runs,
and `withSiteFacet` offers it only when there are two sites. The `?session=` deep link still opens
its run: the hook drops that key only on the curator's next filter change, after it has been used.

**The agent option is a vocabulary word, not a console feature.** The browser's "More filters"
popover has a "Written by an agent" checkbox that sets the URL's `agent=true`, which travels like
every other filter. It is a checkbox and not a value in the author field because an agent is not
an owner: its writes carry the credential's user, so no name typed there could match one.

**Facet counts come from the listing, when asked.** The browser adds `facets=true` to its first
page and to a re-read after a write. The response then carries `facets`, the filtered posts counted
by type name, state and locale. `withCounts` writes each count into its option's label ("Article ·
12"), since the bar has no count slot and its labels are the product's. An option the listing has
none of leaves the menu (SH1185), except the chosen one: every caller passes its `barValue`, and a
chosen value stays at 0 so it can be seen and cleared. A choice facet left with nothing to offer
leaves the bar. With
counts, the locale facet is built from their keys, which cover the whole listing, not from one page's
rows. A "load more" page keeps the first page's counts.

**The lists that hold their rows count them too.** The trash, the review queue and search pass
`countLocally` counts through `withCounts`: over the rows the other filters leave, with a row
counted once per value. A run counts once per site it touched, and a live draft counts as both
draft and published, as choosing either would list it.

**Saved views sit in every bar's `views` slot.** `<SavedViews list="browser|search|trash|review">`
saves `location.pathname` and `location.search` under a name, which is the list's whole state
(SH1023). Opening one navigates to `viewHref`. A view is its owner's, and optionally offered to the
tenant. Saving the same name again replaces it, and only the owner deletes it. The store is
`/api/v2/user/current/views` (`ShSavedViewService`, `sh_saved_view`), classified in
`surfaces.json` as a console-only preference like the dock: an agent states its query every time.

## The browser's three views (SH1026, in parts)

### The browser is a directory, and the page is 592 lines (SH1145)

SH1026's design asked for this split "because at that size the next feature makes it unreviewable",
and moved what each view *draws*. The page kept three concerns tangled in one component and stood at
**1,593 lines**. The cost was never only size, which is the part worth keeping: SH1026's own live
check found the listing effect **re-reading page 0 after every render**, because a value built in
the component body fed a callback the effect depended on. Nothing in a file that size makes that
visible, and the lint that could have seen it was answering about a different line.

What that argues for is units whose **dependencies are the whole of their inputs**:

| Module | What it owns |
|---|---|
| `use-browser-listing.ts` | every read: the page, the next page, the re-read after a write, the facet counts, the breadcrumb, the select-everything walks. Takes the folder, the committed query and the sort. |
| `use-browser-keyboard.ts` | the listbox: roving focus, ranges, Space, Enter, Delete, and `/`. Takes the rows and what a key ends up doing. |
| `use-bulk-verbs.ts` | the plan, the run, and the two pieces of state between them. |
| `use-browser-open.ts` | the three verbs that leave the list: duplicate, open the site, open a page. |
| `browser-toolbar.tsx` | both toolbars — markup, no state. |
| `folder-picker.dialog.tsx` · `bulk-plan.dialog.tsx` | the two dialogs the page used to hold inline. |
| `row-actions.ts` · `browser-listing.ts` | the seven verbs a row offers; the envelope, its rows and the request constants. |

**Fourteen suites read this page by name, and that is the part that could have gone wrong quietly.**
A test left matching regexes against a 592-line page that no longer holds the code it names keeps
**passing** — SH1118's failure shape, reached by relocation instead of by a stripper. So
`test/support/browser-source.mjs` gives a scan whose subject is *the browser* the page plus every
module under `content-browser/`, read from the directory so tomorrow's module is in the subject
without anybody remembering. A scan whose subject is one file — every `doesNotMatch` saying a
particular module must not do a particular thing — keeps naming that file, because widening those
would weaken them.

Four assertions needed their **spelling** updated rather than their subject, and each says so at the
call: the delete key now calls the `onDelete` the page supplies (both halves asserted), the plan
sheet reads a non-null `plan`, the query identity is built from `listKey`, and the picker's blocked
set is computed by the page and handed over in the request.

Three things the split turned up that were not size:

- **The picker's own read did not cancel.** It walks folders that are clickable while the read is in
  flight, so a curator going down two quickly could have the first answer land after the second.
  SH1047's rule applied to it the moment it became a file that rule walks.
- **An unnamed icon stopped being a page's debt.** The post-type grid button carried a Tooltip and no
  accessible name, which SH893 tolerated as declared debt *on a page*; in a shared component that
  rule says fix it, and it is right — a Tooltip needs hover or focus to exist at all.
- **`SH1142`'s own lint went blind for one run.** The Select menu's items call `onSelectAction` in
  the toolbar rather than `handleSelectAction`, so five of its six controls vanished from the scan
  and only the page's Clear was left. Its **count floor** is what said so — the assertion that a
  reader finding suspiciously little has stopped reading, not started agreeing.

The list, grid and table buttons used to colour themselves and nothing else: the render below was
one unconditional map. `content/content-browser/` now holds the views. `browser-view.ts` is pure
(`browserView` reads the URL's `view` word, an unknown one is `list`; `stepFor`, `listboxClass`,
`optionClass`; the row type `BrowserRow` and `rowLabel`; the table layout's storage key and its
guarded parse). `browser-views.tsx` draws the list row and the card. The page keeps **one** listbox
for both, so selection, the roving focus, Shift-extend, Delete and the row actions (SH1020) are one
implementation. In the grid, Left and Right step through the same order as Up and Down, and
`aria-orientation` says so. The buttons are named (`content.viewAs*`) and carry a literal
`aria-pressed`.

**The table is `BentoDataTable` (`browser-table.tsx`).** It brings the virtualised body, sortable
headers and the column picker. The layout is kept in `localStorage` under the signed-in user's
name. The table owns its own selection and keyboard: it reports the selection through
`onSelectionChange`, and the page's bulk verbs are its `selectionActions`, because without them
its rows are not selectable at all. Its row actions are the page's eight, looked up by position
on the row they are chosen for. The selection holds within `scope`, the folder plus `list.key`. The
page imports it **statically**: the package's bento entry is one module, so the table's code is
eager whichever way it is imported, and `lazy()` measured *heavier* (1673.5 against 1671.1 KB raw)
and regrouped the eager chunks. That cost (~44 KB raw, ~13 KB gzip) is recorded where the eager
ceilings were raised, in `console-size-budget.json`.

**A row's title edits in place (`title-cell.tsx`).** The listing carries neither field names nor
versions, so an edit starts with a read of the draft. That read supplies the post type's title field
(`editableTitleField`: exactly one top-level `isTitle` field with a Text or Text Area widget,
otherwise the cell says the title is edited in the post). It also leaves its ETag in
`ShPostUnifiedService`, so the save is the merge verb (`PATCH`, SH335) with `If-Match` (SH985). A
write by somebody else since the edit opened is a 409 whose problem says to read again; the cell
forgets the stale version so the next try reads afresh. Enter and Escape inside the input belong to
the edit, not to the table's row keys. `ShPostUnifiedAPITest` asserts the refusal on `PATCH`.

The accent debt, the SH1020 menu guard and the SH1059 label guards read the `content-browser/`
files, and `browser-views.test.mjs` holds the rest.

### One control for one selection, because the table takes none in (SH1142)

The table **reports** its selection and cannot be told one: `BentoDataTableProps` has
`onSelectionChange` and no `selectedIds`, where the same component's column `layout` *is* controlled
beside `onLayoutChange`. One of the two was finished.

So the flow ran one way, and two controls wrote the page's set behind the table's back — the
toolbar's Select menu (content, folders, everything, invert, nothing) and the selection bar's Clear.
In the table view nothing on screen changed, and the next click in the table reported its own set and
overwrote the choice. **Pick "Select all", then Delete, and the rows deleted were whatever the table
last had ticked while the confirm dialog counted the page's set** — the number and the rows could
disagree, on a destructive verb.

Both are now absent in the table view rather than inert: the table's own header checkbox selects all
and Escape clears. `SH1148` is the other half, on the package's side, and this comes back in every
view the moment it lands — a curator in the table view has no select-everything until then, which is
a real subtraction and the reason that line exists.

**The assertion is not §LXIX.11's, and the substitution is the point.** That one was to choose
"Select all" and compare the table's checked rows with the page's selection, which describes the
*design system's* fix and cannot be written once there is no "Select all" in that view to choose.
`SH1142: no control outside the table writes the selection in the table view` states what this
correction actually establishes, and holds it as a **rule over every writer** rather than as two
spot-checks on the two that were wrong — because the recurrence is a third control added to that
toolbar by somebody who never read this. It walks braces from each `{viewMode !== "spreadsheet" &&`
guard and asserts every JSX `onClick` naming `setSelectedItems` or `handleSelectAction` falls inside
one. Verified both ways: ungating the menu fails it, and so does a newly added ungated button.

One trap worth keeping, met while writing that reader. It blanks comment lines in place to keep its
offsets, and the first draft's predicate was `//`, `*`, `/*` — so a JSX comment's closing `*&#47;}`
line was blanked (it starts with `*`) and took its `}` with it, while the opening `{/*` line was not.
Three such comments on the page, three unbalanced braces, and the brace walk ran to the end of the
file. `{/*` is in the predicate now. It is SH1118's class again: a per-line comment test that is
right about the shapes it was written for and silently wrong about one more.

**Cards show an image.** Each listing row carries `thumbnail`, the byte path of an image. A `File`
post holding an image is its own thumbnail. Any other post uses the first image it references, from
SH1034's reference index. `ShConsoleThumbnails` answers for a whole page in two statements (its
edges, then their targets), never one per card. What counts as an image is `ShAssetKind`, the one
rule verify's alt-text check also uses. The card asks the transform pipeline for its own size
(`thumbnailSrc`: 320×192, cropped, webp) and falls back to the kind's icon. The image is decorative,
since the title below names the row.

**The list and the grid are windowed past 150 rows.** `useRowWindow` (in `content-browser/`)
mounts only the rows the viewport shows, plus four either side. Spacers stand in for the rest, so
the scrollbar stays true. A smaller folder mounts whole, as before. Geometry is measured, not
declared: a row's height is the first mounted option's plus the row gap, and the grid's columns are
its computed tracks. It re-measures on any scroll (a capturing document listener, since the shell
may scroll its own element) and on resize, and it only sets state from those callbacks. The
arithmetic is `visibleWindow`, which is pure and never returns an empty window: a list below the
fold keeps its first rows, which is also what the next measure reads a row height from.

The keyboard still reaches every row. An arrow to an unmounted row scrolls it into the window
(`reveal`) and focuses it once it renders. With the focused row scrolled away, the first mounted row
holds the tab stop. Options carry `aria-setsize` and `aria-posinset`, so a screen reader counts the
list and not the window. `browser-views.test.mjs` checks the arithmetic and the wiring.
`e2e/browser-window.check.mjs` drives it over a folder of 200 posts on a live console. It
confirmed 19 of 200 rows mounted in the list and 39 in the grid, End and Home reaching the last and
first rows, and an idle page making no reads.

**That run found a request loop.** `filters` was rebuilt from `list.value` on every render, so
`fetchPage` was new every render, and the listing effect re-read page 0 after each one. The result
was a continuous stream of `/list?page=0` that also threw away every "load more". `filters` is now
memoised on `list.key`, the committed query string. The check's idle-read claim holds that.

Moving the rest of the page into the directory (the listing reads, the listbox keyboard, the
dialogs) is SH1145.

## The Forms page (SH1066)

`content/forms.page.tsx`, on the rail as **Forms** (`surfaces.json` id `forms`, agent counterpart
`shio_find` with `submission=true`). It lists one site's submissions through `/content/search` with
`submission: "true"`, the same question an agent asks, and edits that site's one row on
`/api/v2/site-form` (`services/site-form/site-form.service.ts`): folder path, allowed post types and
allowed origins, each stored comma-separated, and the enabled switch. Saving creates the row the
first time and updates it after, because SH632 refuses a second configuration for a site with 409.
The site is in the URL (`?site=<name>`).

**Trash is the only row action.** The design asked for archive and mark-as-spam, but a post has no
archive state. SH970 already chose trash for the arrival report, and the trash is where a submission
can be restored from. The arrival's caption is still the post type's title and summary fields, not a
setting of the form. `GET /api/v2/site-form` is declared in `ShConsoleReadAclLintTest`'s `UNCHECKED`:
it is operator configuration, not content.

## The chip that could not say which language is the original (SH1125)

SH1059 put each post row's locale on a chip beside the post-type badge and named the row by it, so a
page and its translation are two rows rather than one row and an apparent duplicate. Its design also
asked that the chip mark the site's **default** locale "where the site's enabled set exists" — and it
did not exist then: the console could not read a site's policy until SH1056 gave it
`GET /api/v2/site/{id}/locales`. It could afterwards and the browser still did not ask, so a folder
holding a page in English and in Portuguese said which row was which and **not which one came first**,
which is the question a curator deleting one of them is actually asking.

**`declared` is the whole rule**, and it is SH1056's distinction rather than a nicety. A site that has
chosen nothing still reports a default — the locale its content happens to be written in — and a chip
saying "default" about something nobody chose is exactly the confusion SH1056 exists to remove. So
`declaredDefaultLocale` returns a locale only for a declared policy, an undeclared one marks no row,
and a folder of two translations then says which language each is and stays silent about which came
first. That is the honest answer, and it is the assertion worth having: the test drives the helper with
`declared: false` and requires `null`.

Three implementation choices, each for a reason that would otherwise be re-derived:

- **The flag is on the row, not computed at the label.** Whether a row is the site's own language is a
  fact about *that row in this site*, and four call sites build a label — the checkbox, the title, the
  actions trigger and the data table. `use-browser-listing` computes `isDefaultLocale` once per listing
  from one `useSiteLocalePolicy(siteId)`, cached under the key the query layer already uses for the
  site, so walking a tree inside one site asks once.
- **The word is passed into `rowLabel` already translated.** That function is pure so
  `browser-locale.test.mjs` can run it without a browser, and a hard-coded `"default"` would be an
  English word in a Portuguese console. The locale *tag* is not translated, because a tag is a tag and
  a sentence about it is not.
- **All four labels and the chip**, including the spreadsheet view's `getRowLabel`. Leaving the table
  as the one view without the mark is the half-migration this repository keeps finding, so it has an
  assertion of its own.

Two guards moved with it and were followed rather than loosened: SH1059's extractor names
`rowLabel`'s signature, and SH1020's names the actions trigger's call. Both would have kept passing
against a version that had lost the argument, which is why the regex is the thing that had to change.

## The Scheduled page has a calendar (SH1082)

`scheduled.page.tsx` switches between its agenda table and design system 2026.3.13's
`BentoCalendar` (VDS172, filed from here), drawn in the zone the page states. Each agenda entry
carries its post's whole pair (`publishAt`, `unpublishAt`), because the schedule write replaces
both. `movedSchedule` in `src/lib/schedule-move.ts` sends the moved side's new instant and the
other side as it was; sending only the moved one would clear the other. The calendar keeps the
time of day, and the page only writes and refetches. `entryKey` is `action|address`, since a post
has at most one of each transition. `onEntrySelect` opens the post.

`e2e/console-check.mjs`'s `scheduled-calendar-drive` is the live half. It schedules a provisioned
post for the 10th of next month at noon UTC, moves it one day with Space, the Right arrow and
Enter, and reads `/agent/find?scheduled=any` back. Noon keeps every runner's zone on the same day,
and next month keeps today's date from making the move cross a month. On its first run the drive
found SH1179: the endpoint had refused every console schedule.

## The editor and the translation workspace are one split (SH1094)

Both pages compose design system 2026.3.13's `BentoSplitPage` (VDS173, filed from here), and
`bento-column.ts`'s `WIDE` table gives both routes the shell's wide column, which the shape is drawn
in. In the editor, the left pane is what a curator writes: the working line, the fields and the
history. The right pane is what they read while writing: the live preview, the insights, and the
note rail, since a note is written while reading the page it is about. The workspace puts the
source on the left and the translation on the right. The ratio is kept per viewer under
`shio.post-editor.split` and `shio.translate.split`. At phone width the package draws one pane and a
tab switch, and neither pane unmounts, so nothing typed is lost to looking at the other.

The workspace used to hand-build this with a `pane` state and `lg:` classes, and at 1440px it
showed one pane with the phone switch still visible. Both panes were in the DOM, so axe and every
source test passed over it. `console-check.mjs`'s `split-pages` probe reads what is **visible**:
two labelled sections at 1440px, and one plus two tabs at 390px, on both routes. Run against the
old workspace, it fails.

## The table view is controlled by the page's selection (SH1148)

`BrowserTable` hands `BentoDataTable` the page's own set as `selectedIds` (design system 2026.3.13,
VDS174, filed from here). The page builds that array once per set with `useMemo`, so an
unchanged selection is an unchanged prop. So the table ticks exactly what the page holds, and the
toolbar's Select menu is back in every view. SH1142 had taken it out of the table view because
the menu wrote a set the table never heard. The page's own selection bar stays out of the table
view only because the table draws a count and **Clear selection** of its own on the same set.

`browser-views.test.mjs` reads both ends of the flow. `console-check.mjs`'s `table-select-all`
chooses "Everything" and then "Invert" in the table view and reads the table's own
`aria-selected` rows and count: 7/7, then 0/7. With `selectedIds` removed it reads 0/7 and fails,
which is SH1142's defect exactly.

## The bulk plan marks its changes the way the diff does (SH1178)

`bulk-plan.dialog.tsx` draws design system 2026.3.13's `BentoChangeMark` (VDS170) per planned item,
in place of `change.action` printed raw in a grey badge. The raw word was English in a Portuguese
console, and it made a deletion look exactly like a no-op. `changeStateOf` (`src/lib/change-mark.ts`)
decides the mapping, because `ShAgentChangeSet.Change`'s vocabulary is wider than the mark's four
states: `create` is added, `delete` is removed, `noop` is unchanged, and `update`, `move`,
`publish` and `unpublish` are changed. A changed row also names its action
(`content.changeAction.*`), since "changed" alone hides what a move does. A refused row keeps its
destructive badge, because a refusal is not a change. `change-mark.test.mjs` holds the mapping for
every action, renders the real mark for each state, and reads the dialog's wiring. The dialog is
TSX, which Node's type stripping cannot import.

## How the console looks is measured, not reviewed (SH1191)

`pnpm look` runs the design system's census (VDS180) against this console. It walks every route
under `/bento` in desktop dark, desktop light and a 390 px phone, attributes each button height,
radius, font, primary fill and title offset to package or product code, and fails when a figure grows
past the allowance the design system keeps for `shio`. The census is not copied here, because a
second census would be a second allowance. `scripts/look-census.mjs` finds the design system's
checkout on the same version line (or `VIGLET_DS_CHECKOUT`) and passes this console's URL and
`SHIO_USER`/`SHIO_PASSWORD`. A console task in the look round proves itself by the figure it lowers,
and `pnpm look -- --write` records the lower reading in the design system's checkout.

## One page anatomy: a title offset per column, and a trail where content nests (SH1192)

A page hero draws **no icon beside its title**. `PageHero` has no `icon` prop, no page passes
`leading` to `BentoHero`, and `BentoListPage` gets no `heroIcon`: an icon there moves the title by
its own width, which is how the console reached four title offsets inside one column. The gradient
chip stays where the design system keeps it (VDS182), on an entity's identity hero. The title's
offset is now the column's: `default` 152 px, `narrow` 344 px (single-question forms only, which is
why the six multi-section infrastructure editors left it), `full` 112 px, and an entity shell's
chip, measured at 1440 px. Content that nests takes the hero's `trail` (VDS184): the folder browser
titles itself with the folder and names its ancestors, and the post editor names the folders it sits
in, from `heroTrail` over the same `/path` read. `page-anatomy.test.mjs` holds all three.

## The rail's second level is a hub, and every surface is on one (SH1193)

The bento navigation is rail, then hub, then item. Each rail section's `areaRoute` is its hub,
`/bento/areas/<section>` (`bento-area.page.tsx`). The rail never links to one of a section's surfaces:
Content used to point at the Sites list and Administration at a redirect to Users, so the second
level never rendered and ten surfaces had no path from the rail. A hub draws its section's surfaces
as tiles in the groups `HUB_GROUPS` declares in `bento-nav.ts`, four tiles or fewer per group and
four groups or fewer per hub. The grouping is presentation, so it lives in the console and not in
`console/surfaces.json`. The surface the declaration marks `navigation` (`admin`) *is* the hub, so
it is never a tile on it; `/bento/admin` redirects there. Users, groups and roles are the `users`
surface, declared operator-only. `bento-hub.test.mjs` fails a surface declared without a group, a
group over four, and a hub that lists itself.

## Records are rows (SH1194)

`AdminListPage` (users, groups, roles, tokens, auth and exchange providers, webhooks, tenants) and the
site list pass `columns` to `BentoListPage` and no `renderTile`, so the package draws a sortable
`BentoDataTable` under the hero with the create action as its one primary (VDS183). The columns are
the fields the tile showed (name linked to the record, description, the tile's meta line as
Details, and status as a dot and a word when the model has one), so no list lost information, and
Enter or a double click opens the row. Tiles are for entities a reader picks by sight or that are
few, such as media, blueprints and a hub; a list page that asks for them is named in
`record-lists.test.mjs` with its reason. The look census counted nine tiled lists before and none
after.

## One filled primary per screen (SH1195)

The primary is `--primary`, which the default `Button` variant already reads, so no button in the
console paints a saturated colour of its own; the site browser's two hardcoded greens were a second
primary by construction. Secondary verbs are outlined, and verbs that are destructive or merely
occasional go in `BentoActionsMenu`: the browser's site actions (Properties, Download Site) and the
trash's permanent delete, one menu per row beside Restore. A verb that cannot run yet is not drawn
disabled: the bulk menu appears once there is a selection. A read-only screen offers no save; a post
type managed by code gets a plain hero and its banner. `one-primary.test.mjs` holds each rule. The
census's `primary-fill` fell from five colours to four; its "more than one filled primary" count
still names the e-mail, token and webhook editors, where the second fill is the save bar's hidden
morph copy and the switches that are on, which is a reading to fix in the census rather than here.

## The dev server proxies every backend prefix the console links to (SH1201)

`vite.config.ts` proxies `/api` and each other top-level prefix the backend serves that the console
builds a URL on: `/file_source` (media bytes and thumbnails), `/preview`, `/sites`, `/store`,
`/graphql`, `/graphiql`, `/logout`, `/csrf` and `/oauth2`. A prefix left out is answered by the dev
server with the SPA shell and a 200, so nothing fails loudly: the media library showed fifteen broken
thumbnails and no request error. `dev-proxy.test.mjs` reads every URL the console source builds and
fails on a backend prefix the proxy does not name.

## The mascot rests where the shell reserves room for it (SH1198)

The dock is handed to `BentoShell` as `dock`, and since 2026.3.15 (VDS185) the shell rests it at the
foot of the rail and, on a phone, in the header, so it can no longer sit over a page's controls. The
look census went from twenty routes with a covered control to eight, and none of the eight is the
dock: they are the sticky header over content scrolled beneath it and the back-to-top control while
hidden. The visual editor mounts it `inline` in its own layout; nothing else mounts it.
`dock-slot.test.mjs` holds both. The bell-with-a-cog in the header is not a second notice: it is the
dock's preferences (SH971), which sit in the header only because neither the assistant nor the user
menu has a slot for them yet.
