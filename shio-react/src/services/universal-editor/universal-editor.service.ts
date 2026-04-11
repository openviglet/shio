import axios from "axios";

export interface ShEditorModel {
  id: string;
  name: string;
  title: string;
  fields: ShEditorModelField[];
}

export interface ShEditorModelField {
  name: string;
  label: string;
  description: string;
  widget: string;
  required: boolean;
  isTitle: boolean;
  isSummary: boolean;
  ordinal: number;
  component: string;
}

export interface ShEditorPostData {
  id: string;
  title: string;
  postType: { id: string; name: string };
  attrs: Record<string, {
    strValue: string;
    dateValue: string;
    intValue: number;
    arrayValue: string[];
    widget: string;
    label: string;
  }>;
}

export interface ShEditorUpdateResult {
  postId: string;
  attrName: string;
  value: unknown;
  status: string;
}

export class ShUniversalEditorService {
  async getModel(postTypeId: string): Promise<ShEditorModel> {
    const response = await axios.get<ShEditorModel>(`/v2/universal-editor/model/${postTypeId}`);
    return response.data;
  }

  async getPostData(postId: string): Promise<ShEditorPostData> {
    const response = await axios.get<ShEditorPostData>(`/v2/universal-editor/post/${postId}`);
    return response.data;
  }

  async updateAttr(postId: string, attrName: string, value: unknown, type: string): Promise<ShEditorUpdateResult> {
    const response = await axios.patch<ShEditorUpdateResult>(
      `/v2/universal-editor/post/${postId}/attr/${attrName}`,
      { value, type }
    );
    return response.data;
  }

  async batchUpdate(postId: string, updates: { attrName: string; value: unknown; type: string }[]): Promise<unknown> {
    const response = await axios.patch(`/v2/universal-editor/post/${postId}`, updates);
    return response.data;
  }
}
