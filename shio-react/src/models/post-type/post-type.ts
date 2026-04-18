export type ShPostType = {
  id: string;
  name: string;
  namePlural: string;
  title: string;
  description: string;
  system: number;
  date: string;
  owner: string;
  workflowPublishEntity?: string;
  schemaJson?: string;
  shPostTypeAttrs?: ShPostTypeAttr[];
};

export type ShPostTypeAttr = {
  id: string;
  name: string;
  label: string;
  description: string;
  ordinal: number;
  required: number;
  isSummary: number;
  isTitle: number;
  shWidget: { id: string; name: string };
  widgetSettings: string;
  shPostTypeAttrs?: ShPostTypeAttr[];
};
