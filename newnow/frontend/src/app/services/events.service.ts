import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EventsService {
  private apiUrl = environment.apiUrl + '/events';

  constructor(private http: HttpClient) {}

  getEventsByLocation(locationId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/byLocation/${locationId}`);
  }

  // ✅ ISPRAVKA: Uklonjen dupli /events
  getAllEvents(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }
}
