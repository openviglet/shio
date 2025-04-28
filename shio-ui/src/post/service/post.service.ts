/*
 * Copyright (C) 2016-2020 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { ShPostXPData } from 'src/post/model/postxp.model';
import { Breadcrumb } from 'src/folder/model/breadcrumb.model';
import { ShPost } from 'src/post/model/post.model';

@Injectable({
    providedIn: 'root'
})
export class ShPostService {

    constructor(private httpClient: HttpClient) { }

    get(id: string | null): Observable<ShPostXPData> {
        return this.httpClient.get<ShPostXPData>(`${environment.apiUrl}/api/v2/post/xp/${id}`);
    }

    getBreadcrumb(id: string | null): Observable<Breadcrumb> {
        return this.httpClient.get<Breadcrumb>(`${environment.apiUrl}/api/v2/folder/${id}/path`)
    }
    public savePost(shPost: ShPost): Observable<Object> {        
        return this.httpClient.put(`${environment.apiUrl}/api/v2/post/${shPost.id}`,
        JSON.stringify(shPost));
    }
}