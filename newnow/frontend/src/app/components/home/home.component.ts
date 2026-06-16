import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
  todayEvents: any[] = [];
  topLocations: any[] = [];
  loading: boolean = true;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadHomepageData();
  }

  loadHomepageData(): void {
    this.http.get(`${environment.apiUrl}/home`).subscribe({
      next: (data: any) => {
        console.log('🏠 Homepage data:', data);
        this.todayEvents = data.todayEvents || [];
        this.topLocations = data.topLocations || [];
        this.loading = false;
      },
      error: (err) => {
        console.error('Greška pri učitavanju:', err);
        this.loading = false;
      }
    });
  }

  getRatingStars(rating: number): string {
    const fullStars = Math.round(rating || 0);
    return '⭐'.repeat(fullStars);
  }

  getReviewsText(count: number): string {
    if (count === 1) return 'utisak';
    if (count >= 2 && count <= 4) return 'utiska';
    return 'utisaka';
  }

  getEventTime(dateTime: string): string {
    const date = new Date(dateTime);
    return date.toLocaleTimeString('sr-RS', {
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getImageUrl(url: string): string {
    if (!url || url === 'placeholder_error')
      return 'https://placehold.co/400x250/F5F5F0/333333?text=NO+IMAGE';

    if (url.startsWith('http')) return url;

    const baseUrl = environment.apiUrl.endsWith('/api')
                    ? environment.apiUrl.replace('/api', '')
                    : environment.apiUrl;

    return baseUrl + '/uploads/' + url;
  }

  onImageError(event: any) {
    event.target.src = this.getImageUrl('placeholder_error');
  }
}
