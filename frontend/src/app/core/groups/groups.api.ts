import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { Group, Page } from '../models';

@Injectable({ providedIn: 'root' })
export class GroupsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  list(page: number, size: number): Observable<Page<Group>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Group>>(`${this.baseUrl}/groups`, { params });
  }

  create(name: string, currency: string): Observable<Group> {
    return this.http.post<Group>(`${this.baseUrl}/groups`, { name, currency });
  }
}
