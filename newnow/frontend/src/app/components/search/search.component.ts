import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.css']
})
export class SearchComponent {
  // Parametri pretrage
  params = {
    name: '',
    description: '',
    pdf: '',
    reviewsMin: null as number | null,
    reviewsMax: null as number | null,
    ratingMin: null as number | null,
    ratingMax: null as number | null,
    perfMin: null as number | null,
    perfMax: null as number | null,
    soundMin: null as number | null,
    soundMax: null as number | null,
    spaceMin: null as number | null,
    spaceMax: null as number | null,
    overallMin: null as number | null,
    overallMax: null as number | null,
    operator: 'AND',
    sortBy: 'name',
    sortOrder: 'asc'
  };

  results: any[] = [];
  similarResults: any[] = [];
  loading = false;
  searched = false;
  errorMsg = '';

  showHelp = false;
  showRatingFilters = false;

  constructor(private http: HttpClient) {}

  search(): void {
    this.loading = true;
    this.searched = false;
    this.errorMsg = '';
    this.results = [];
    this.similarResults = [];

    const queryParams: any = {};
    if (this.params.name?.trim()) queryParams['name'] = this.params.name.trim();
    if (this.params.description?.trim()) queryParams['description'] = this.params.description.trim();
    if (this.params.pdf?.trim()) queryParams['pdf'] = this.params.pdf.trim();
    if (this.params.reviewsMin != null) queryParams['reviewsMin'] = this.params.reviewsMin;
    if (this.params.reviewsMax != null) queryParams['reviewsMax'] = this.params.reviewsMax;
    if (this.params.ratingMin != null) queryParams['ratingMin'] = this.params.ratingMin;
    if (this.params.ratingMax != null) queryParams['ratingMax'] = this.params.ratingMax;
    if (this.params.perfMin != null) queryParams['perfMin'] = this.params.perfMin;
    if (this.params.perfMax != null) queryParams['perfMax'] = this.params.perfMax;
    if (this.params.soundMin != null) queryParams['soundMin'] = this.params.soundMin;
    if (this.params.soundMax != null) queryParams['soundMax'] = this.params.soundMax;
    if (this.params.spaceMin != null) queryParams['spaceMin'] = this.params.spaceMin;
    if (this.params.spaceMax != null) queryParams['spaceMax'] = this.params.spaceMax;
    if (this.params.overallMin != null) queryParams['overallMin'] = this.params.overallMin;
    if (this.params.overallMax != null) queryParams['overallMax'] = this.params.overallMax;
    queryParams['operator'] = this.params.operator;
    queryParams['sortBy'] = this.params.sortBy;
    queryParams['sortOrder'] = this.params.sortOrder;

    this.http.get<any[]>(`${environment.apiUrl}/search/locations`, { params: queryParams })
      .subscribe({
        next: (data) => {
          this.results = data;
          this.loading = false;
          this.searched = true;
        },
        error: (err) => {
          this.errorMsg = 'Greška pri pretrazi. Provjeri da li je Elasticsearch pokrenut.';
          this.loading = false;
          this.searched = true;
        }
      });
  }

  loadSimilar(locationId: string, event: MouseEvent): void {
    event.preventDefault();
    this.http.get<any[]>(`${environment.apiUrl}/search/locations/${locationId}/similar`)
      .subscribe({
        next: (data) => { this.similarResults = data; },
        error: (err) => console.error('MLT greška:', err)
      });
  }

  downloadPdf(locationId: string, event: MouseEvent): void {
    event.preventDefault();
    window.open(`${environment.apiUrl}/search/locations/${locationId}/pdf`, '_blank');
  }

  clearAll(): void {
    this.params = {
      name: '', description: '', pdf: '',
      reviewsMin: null, reviewsMax: null,
      ratingMin: null, ratingMax: null,
      perfMin: null, perfMax: null,
      soundMin: null, soundMax: null,
      spaceMin: null, spaceMax: null,
      overallMin: null, overallMax: null,
      operator: 'AND', sortBy: 'name', sortOrder: 'asc'
    };
    this.results = [];
    this.searched = false;
    this.errorMsg = '';
    this.similarResults = [];
  }

  getHighlight(result: any, field: string): string | null {
    if (result.highlights && result.highlights[field] && result.highlights[field].length > 0) {
      return result.highlights[field].join(' ... ');
    }
    return null;
  }
}
