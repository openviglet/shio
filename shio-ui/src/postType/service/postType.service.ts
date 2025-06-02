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
import { ShPostType } from 'src/postType/model/postType.model';

@Injectable({
    providedIn: 'root'
})
export class ShPostTypeService {

    constructor(private httpClient: HttpClient) { }

    get(id: string | null): Observable<ShPostType> {
        return this.httpClient.get<ShPostType>(`${environment.apiUrl}/api/v2/post/type/${id}`);
    }
    query(): Observable<ShPostType[]> {
        return this.httpClient.get<ShPostType[]>(`${environment.apiUrl}/api/v2/post/type`)
    }
    public savePost(shPostType: ShPostType): Observable<Object> {        
        return this.httpClient.put(`${environment.apiUrl}/api/v2/post/type/${shPostType.id}`,
        JSON.stringify(shPostType));
    }
}