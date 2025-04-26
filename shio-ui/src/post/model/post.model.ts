import { ShSite } from 'src/repository/model/site.model';
import { ShPostAttr } from './postAttr.model';
import { ShPostType } from 'src/postType/model/postType.model';

export interface ShPost {
    id: string;
    date: Date;
    title: string;
    position: number;
    shSite: ShSite;
    shPostType: ShPostType;
    shPostAttrs: ShPostAttr[];
    summary: string;
}
