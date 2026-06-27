import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { resolveMediaUrl } from '../../utils/media-url';

@Component({
  selector: 'app-locations',
  standalone: true,
  imports: [CommonModule, HttpClientModule, RouterModule, FormsModule],
  templateUrl: './locations.html',
  styleUrls: ['./locations.css']
})
export class LocationsComponent implements OnInit {
  locations: any[] = [];
  locationsWithRatings: any[] = [];
  filteredLocations: any[] = []; // 🟢 NOVO - Za filtrirane rezultate

  // 🟢 NOVO - Search i filter parametri
  searchQuery: string = '';
  selectedType: string = '';
  availableTypes: string[] = []; // 🟢 Dinamička lista tipova

  constructor(private http: HttpClient, private router: Router) {}

  ngOnInit(): void {
    this.loadLocations();
  }

  loadLocations(): void {
    this.http.get(environment.apiUrl + '/locations').subscribe({
      next: (data: any) => {
        console.log('📍 Učitane lokacije:', data);

        const filteredLocations = data.filter((loc: any) =>
          loc && loc.name && loc.name.trim().length > 0 && loc.address
        );

        this.locations = filteredLocations;
        this.extractAvailableTypes(); // 🟢 Izvuci tipove
        this.loadRatingsForAll();
      },
      error: (err) => console.error('⚠️ Greška:', err)
    });
  }

  // 🟢 NOVO - Izvuci sve tipove mesta iz učitanih lokacija
  extractAvailableTypes(): void {
    const types = this.locations.map(loc => loc.type).filter(type => type);
    this.availableTypes = [...new Set(types)]; // Ukloni duplikate
    console.log('🎛️ Dostupni tipovi:', this.availableTypes);
  }

  loadRatingsForAll(): void {
    let loadedCount = 0;
    this.locationsWithRatings = [];

    this.locations.forEach(location => {
      this.http.get(`${environment.apiUrl}/locations/${location.id}/details`)
        .subscribe({
          next: (details: any) => {
            this.locationsWithRatings.push({
              ...location,
              averageRating: details.averageRating || 0,
              totalReviews: details.totalReviews || 0
            });

            loadedCount++;
            if (loadedCount === this.locations.length) {
              this.filteredLocations = [...this.locationsWithRatings];
              this.sortLocationsByRating(); // 🟢 Sortiraj po oceni
            }
          },
          error: (err) => console.error('Greška pri učitavanju ocene:', err)
        });
    });
  }

  // 🟢 NOVO - Sortiranje po oceni (opadajuće)
  sortLocationsByRating(): void {
    this.filteredLocations.sort((a, b) => {
      if (b.averageRating !== a.averageRating) {
        return b.averageRating - a.averageRating;
      }
      return b.totalReviews - a.totalReviews;
    });
  }

  // 🟢 NOVO - Pretraga i filtriranje
  applyFilters(): void {
    let results = [...this.locationsWithRatings];

    // 1. Search po nazivu ili adresi
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      results = results.filter(loc =>
        loc.name.toLowerCase().includes(query) ||
        loc.address.toLowerCase().includes(query)
      );
    }

    // 2. Filter po tipu
    if (this.selectedType) {
      results = results.filter(loc => loc.type === this.selectedType);
    }

    this.filteredLocations = results;
    this.sortLocationsByRating();
    console.log('🔍 Filtrirano:', this.filteredLocations.length, 'lokacija');
  }

  // 🟢 NOVO - Reset filtera
  resetFilters(): void {
    this.searchQuery = '';
    this.selectedType = '';
    this.filteredLocations = [...this.locationsWithRatings];
    this.sortLocationsByRating();
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

  viewLocationDetails(locationId: number): void {
    this.router.navigate(['/locations', locationId]);
  }

  getImageUrl(url: string): string {
    return resolveMediaUrl(url);
  }

  downloadPdf(locationId: number, event: Event): void {
    event.stopPropagation();
    window.open(`${environment.apiUrl}/search/locations/${locationId}/pdf`, '_blank');
  }

  onImageError(event: any) {
    event.target.src = this.getImageUrl('placeholder_error');
  }
}
