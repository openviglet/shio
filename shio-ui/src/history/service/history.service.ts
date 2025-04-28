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
import { ShHistory } from 'src/history/model/history.model';

@Injectable()
export class ShHistoryService {
    constructor(private httpClient: HttpClient) { }

    findBySite(id: string | null, page: number): Observable<ShHistory[]> {
        return this.httpClient.get<ShHistory[]>(`${environment.apiUrl}/api/v2/history/object/${id}/${page}`);
    }
    countBySite(id: string): Observable<number> {
        return this.httpClient.get<number>(`${environment.apiUrl}/api/v2/history/object/${id}/count`);
    }
}