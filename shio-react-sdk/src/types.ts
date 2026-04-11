/** Shio CMS site */
export interface ShSite {
  id: string;
  name: string;
  url: string;
  description: string;
  furl: string;
}

/** Object in a listing (folder or post) */
export interface ShObjectItem {
  id: string;
  title: string;
  summary: string;
  objectType: "POST" | "FOLDER";
  date: string;
  position: number;
  shPostType?: { id: string; name: string };
  publishStatus?: string;
  published?: boolean;
}

/** Folder path / breadcrumb */
export interface ShFolderPath {
  shSite: ShSite;
  currentFolder: { id: string; name: string } | null;
  breadcrumb: { id: string; name: string; rootFolder: number }[];
}

/** Raw post attribute from API */
export interface ShPostAttrRaw {
  id: string;
  strValue: string | null;
  arrayValue: string[] | null;
  dateValue: string | null;
  intValue: number | null;
  shPostTypeAttr: {
    id: string;
    name: string;
    label: string;
    ordinal: number;
    required: number;
    isTitle: number;
    isSummary: number;
    shWidget: { name: string };
    widgetSettings: string | null;
  };
  shChildrenRelatorItems?: ShRelatorItem[];
}

/** Relator item (nested content) */
export interface ShRelatorItem {
  id: string;
  title: string;
  ordinal: number;
  shChildrenPostAttrs: ShPostAttrRaw[];
}

/** Raw post from API */
export interface ShPostRaw {
  id: string;
  title: string;
  summary: string;
  furl: string;
  date: string;
  published: boolean;
  publishStatus: string;
  owner: string;
  shPostType: { id: string; name: string; title: string };
  shFolder: { id: string; name: string };
  shPostAttrs: ShPostAttrRaw[];
}

/** Simplified post with key-value attributes map */
export interface ShPost {
  id: string;
  title: string;
  summary: string;
  furl: string;
  date: string;
  postType: string;
  folderId: string;
  attrs: Record<string, string | string[] | null>;
  rawAttrs: ShPostAttrRaw[];
}

/** SDK configuration */
export interface ShioConfig {
  baseUrl: string;
  apiToken?: string;
}
