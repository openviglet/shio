import {ShPostTypeAttr} from "./postTypeAttr.model";

export interface ShPostType {
    id: string;
    title: string;
    system: number;
    name: string;
    description: string;
    namePlural: string;
    shPostTypeAttrs : ShPostTypeAttr[];
}
