export type ShFolder = {
  id: string;
  name: string;
  description: string;
  position: number;
  date: string;
  parentFolder?: ShFolder;
  shSite?: { id: string; name: string };
  breadcrumb?: { id: string; name: string; type: string }[];
};
