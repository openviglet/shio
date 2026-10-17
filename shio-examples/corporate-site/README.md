# Corporate Site Example

A corporate website example for Viglet Shio demonstrating the Universal Editor capabilities.

## Structure

- `export.json` - Shio Exchange format file for importing into Viglet Shio
- `index.html` - Standalone HTML page with `data-sh-*` instrumentation
- `about.html` - About page with editable components

## How to Import

1. Create a ZIP file containing `export.json` and any static files
2. In Viglet Shio, go to **Content > Import Site**
3. Upload the ZIP file

## How to Use with Universal Editor

1. Import the site into Viglet Shio
2. Navigate to **Universal Editor** in the sidebar
3. Select the "Viglet Corporate" site
4. Switch to **Edit Mode** and click on any highlighted element to edit inline

## Universal Editor Data Attributes

Each editable element uses these attributes:

- `data-sh-resource="urn:shio:<postId>"` - Links to the Shio post
- `data-sh-type="text|richtext|media"` - Defines edit behavior
- `data-sh-prop="<fieldName>"` - Maps to the post attribute
- `data-sh-label="<label>"` - Shows on hover in edit mode

The `shio-editor.js` script (included at the bottom) handles all communication
with the Shio Universal Editor shell.
