import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import {
  Expense,
  Group,
  GroupBalances,
  Member,
  Page,
  Settlement,
  SettlementSuggestions,
} from '../models';

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

  get(groupId: string): Observable<Group> {
    return this.http.get<Group>(`${this.baseUrl}/groups/${groupId}`);
  }

  members(groupId: string): Observable<Member[]> {
    return this.http.get<Member[]>(`${this.baseUrl}/groups/${groupId}/members`);
  }

  expenses(groupId: string, page: number, size: number): Observable<Page<Expense>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Expense>>(`${this.baseUrl}/groups/${groupId}/expenses`, { params });
  }

  balances(groupId: string): Observable<GroupBalances> {
    return this.http.get<GroupBalances>(`${this.baseUrl}/groups/${groupId}/balances`);
  }

  settlements(groupId: string, page: number, size: number): Observable<Page<Settlement>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Settlement>>(`${this.baseUrl}/groups/${groupId}/settlements`, {
      params,
    });
  }

  suggestions(groupId: string): Observable<SettlementSuggestions> {
    return this.http.get<SettlementSuggestions>(
      `${this.baseUrl}/groups/${groupId}/settlements/suggestions`,
    );
  }
}
