import { ShPostTypeAttr } from '../../postType/model/postTypeAttr.model';

export interface ShPostAttr {
    id: string;
    shPostTypeAttr: ShPostTypeAttr;
    tab: number;
    strValue: string;
}
