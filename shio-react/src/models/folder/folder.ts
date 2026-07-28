export type ShFolder = {
  id: string;
  name: string;
  description: string;
  position: number;
  date: string;
  parentFolder?: ShFolder;
  shSite?: { id: string; name: string };
  /** When the folder was trashed (SH200); absent on a live folder. */
  deletedDate?: string;
  breadcrumb?: { id: string; name: string; type: string }[];
};
