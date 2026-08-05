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
- Sticky save bar with post title, post type badge, field count badge, publish permission badge
- Save, Save & Close, Cancel buttons
- Card with `PostFormFields` rendering color-coded field cards
- Loading state with animated spinner
- Navigation: back to parent folder on save/cancel

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
