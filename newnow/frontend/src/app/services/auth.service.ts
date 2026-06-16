import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasToken());
  isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(private http: HttpClient) {}

  private hasToken(): boolean {
    return !!localStorage.getItem('token');
  }

  login(userName: string, userRole: string) {
    localStorage.setItem('userName', userName);
    localStorage.setItem('userRole', userRole);
    this.isAuthenticatedSubject.next(true);
  }

  logout() {
    const headers = this.getAuthHeaders();

    this.http.post(`${environment.apiUrl}/auth/logout`, {}, { headers }).subscribe({
      next: () => {},
      error: () => {}
    });

    localStorage.removeItem('token');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('userRole');
    localStorage.removeItem('userName');
    this.isAuthenticatedSubject.next(false);
  }

  isLoggedIn(): boolean {
    return this.hasToken();
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getAuthHeaders(): HttpHeaders {
    const token = this.getToken();
    return token
      ? new HttpHeaders({ Authorization: `Bearer ${token}` })
      : new HttpHeaders();
  }

  getUserName(): string {
    return localStorage.getItem('userName') || '';
  }

  getUserRole(): string {
    return localStorage.getItem('userRole') || 'USER';
  }

  isAdmin(): boolean {
    return this.getUserRole() === 'ADMIN';
  }
}
