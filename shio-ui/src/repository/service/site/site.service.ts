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
import { ShSite } from 'src/repository/model/site.model';
import { ShPostTypeReport } from 'src/postType/model/postTypeReport.model';

@Injectable()
export class ShSiteService {
    constructor(private httpClient: HttpClient) { }
    query(): Observable<ShSite[]> {
        return this.httpClient.get<ShSite[]>(`${environment.apiUrl}/api/v2/site`);
    }

    get(id: string | null): Observable<ShSite> {
        return this.httpClient.get<ShSite>(`${environment.apiUrl}/api/v2/site/${id}`);
    }
    countPostType(id: string): Observable<ShPostTypeReport[]> {
        return this.httpClient.get<ShPostTypeReport[]>(`${environment.apiUrl}/api/v2/site/${id}/type/count`);
    }

    public save(shSite: ShSite): Observable<Object> {


        return this.httpClient.put(`${environment.apiUrl}/api/v2/site/${shSite.id}`,
            JSON.stringify(shSite));

    }

}