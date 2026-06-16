import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { EventsService } from '../../services/events.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-events-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './events-page.component.html',
  styleUrls: ['./events-page.component.css']
})
export class EventsPageComponent implements OnInit {
  allEvents: any[] = [];
  filteredEvents: any[] = [];
  isLoading = true;

  // Search & Filter
  searchQuery = '';
  selectedType = '';
  selectedPriceFilter = '';
  showOnlyToday = true; // 🟢 NOVO - default prikazuje samo današnje

  eventTypes: string[] = [];

  constructor(private eventsService: EventsService) {}

  ngOnInit(): void {
    this.loadAllEvents();
  }

  loadAllEvents(): void {
    this.isLoading = true;
    this.eventsService.getAllEvents().subscribe({
      next: (data: any[]) => {
        console.log('📅 Svi događaji:', data);
        this.allEvents = data || [];
        this.extractEventTypes();
        this.applyFilters(); // 🟢 Automatski primeni filtere (uključujući "samo danas")
        this.isLoading = false;
      },
      error: (err) => {
        console.error('❌ Greška pri učitavanju događaja:', err);
        this.isLoading = false;
      }
    });
  }

  extractEventTypes(): void {
    const types = this.allEvents
      .map(e => e.type)
      .filter((value, index, self) => value && self.indexOf(value) === index);
    this.eventTypes = types;
  }

  applyFilters(): void {
    let result = [...this.allEvents];

    // 🟢 NOVO - Filter samo današnjih događaja
    if (this.showOnlyToday) {
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      const tomorrow = new Date(today);
      tomorrow.setDate(tomorrow.getDate() + 1);

      result = result.filter(e => {
        const eventDate = new Date(e.dateTime);
        eventDate.setHours(0, 0, 0, 0);
        return eventDate.getTime() === today.getTime();
      });
    }

    // Search by title, location name OR ADDRESS 🟢 NOVO
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      result = result.filter(e =>
        e.title?.toLowerCase().includes(query) ||
        e.location?.name?.toLowerCase().includes(query) ||
        e.location?.address?.toLowerCase().includes(query) // 🟢 NOVO - pretraga po adresi
      );
    }

    // Filter by type
    if (this.selectedType) {
      result = result.filter(e => e.type === this.selectedType);
    }

    // Filter by price
    if (this.selectedPriceFilter === 'free') {
      result = result.filter(e => e.price === 0);
    } else if (this.selectedPriceFilter === 'paid') {
      result = result.filter(e => e.price > 0);
    }

    this.filteredEvents = result;
  }

  // 🟢 NOVO - Toggle prikaz svih vs samo današnjih
  toggleShowToday(): void {
    this.showOnlyToday = !this.showOnlyToday;
    this.applyFilters();
  }

  resetFilters(): void {
    this.searchQuery = '';
    this.selectedType = '';
    this.selectedPriceFilter = '';
    this.showOnlyToday = true; // 🟢 Reset na "samo danas"
    this.applyFilters();
  }

  getImageUrl(url: string): string {
    if (!url || url === 'placeholder_error')
      return 'https://placehold.co/400x300/667eea/ffffff?text=EVENT';

    if (url.startsWith('http')) return url;

    const baseUrl = environment.apiUrl.endsWith('/api')
      ? environment.apiUrl.replace('/api', '')
      : environment.apiUrl;

    return baseUrl + '/uploads/' + url;
  }

  onImageError(event: any): void {
    event.target.src = this.getImageUrl('placeholder_error');
  }

  getEventDate(dateTime: string): string {
    const date = new Date(dateTime);
    return date.toLocaleDateString('sr-RS', {
      day: '2-digit',
      month: 'long',
      year: 'numeric'
    });
  }

  getEventTime(dateTime: string): string {
    const date = new Date(dateTime);
    return date.toLocaleTimeString('sr-RS', {
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
