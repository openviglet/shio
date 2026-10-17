# Viglet Shio Examples

Example sites demonstrating Viglet Shio features, including the Universal Editor.

## Examples

### 1. Corporate Site (`corporate-site/`)
A corporate website with hero sections, service cards, and call-to-action components.
Demonstrates visual editing of structured page content.

**Post Types:** Page, Service

### 2. Blog Site (`blog-site/`)
A modern tech blog with article listings and full post pages.
Demonstrates visual editing of blog/article content.

**Post Types:** Article

## How to Import into Viglet Shio

Each example contains an `export.json` file in the Shio Exchange format:

1. Create a ZIP file containing the `export.json`:
   ```bash
   cd corporate-site && zip corporate-site.zip export.json && cd ..
   cd blog-site && zip blog-site.zip export.json && cd ..
   ```
2. In Viglet Shio, navigate to **Content > Import Site**
3. Upload the ZIP file
4. The site, folders, posts, and post types will be created

## Using the Universal Editor

After importing a site:

1. Go to **Universal Editor** in the Shio Console sidebar
2. Select the imported site
3. Click **Edit** to switch to edit mode
4. Click on any highlighted element to edit it inline
5. Click **Save** to persist changes

## HTML Pages

Each example includes standalone HTML files (`index.html`, etc.) that demonstrate
how to instrument pages with `data-sh-*` attributes for the Universal Editor.

### Required Data Attributes

| Attribute | Purpose | Example |
|-----------|---------|---------|
| `data-sh-resource` | Links to a Shio post | `urn:shio:<post-id>` |
| `data-sh-type` | Edit behavior | `text`, `richtext`, `media`, `container`, `component` |
| `data-sh-prop` | Maps to a post attribute | `TITLE`, `BODY`, `COVER_IMAGE` |
| `data-sh-label` | Hover label in edit mode | `Page Title`, `Body Content` |
| `data-sh-model` | Post type reference | `Page`, `Article` |

### Including the Editor Bridge

Add at the bottom of your page:

```html
<script src="/universal-editor/shio-editor.js"></script>
```

The script automatically detects when the page is loaded inside the Shio Universal
Editor iframe and enables inline editing. When loaded standalone, it does nothing.
