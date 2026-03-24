export type ShPost = {
  id: string;
  title: string;
  summary: string;
  furl: string;
  shPostType: { id: string; name: string; title: string };
  shFolder?: { id: string; name: string; shSite?: { id: string; name: string } };
  owner: string;
  date: string;
  published: boolean;
  publishStatus: string;
  shPostAttrs: ShPostAttr[];
  breadcrumb?: { id: string; name: string; type: string }[];
};

export type ShPostAttr = {
  id: string;
  strValue: string;
  arrayValue: string[];
  dateValue: string;
  intValue: number;
  shPostTypeAttr: {
    id: string;
    name: string;
    label: string;
    description: string;
    ordinal: number;
    required: number;
    isSummary: number;
    isTitle: number;
    shWidget: { name: string };
    widgetSettings?: string;
  };
  shChildrenPostAttrs?: ShPostAttr[];
  shChildrenRelatorItems?: ShRelatorItem[];
};

export type ShRelatorItem = {
  id: string;
  title?: string;
  summary?: string;
  ordinal: number;
  shChildrenPostAttrs: ShPostAttr[];
};
