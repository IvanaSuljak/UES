import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { EventsService } from '../../services/events.service';
import { resolveMediaUrl } from '../../utils/media-url';

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
  selectedDate = '';
  showOnlyToday = true;

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
      .map(e => this.normalizeEventType(e.type))
      .filter((value, index, self) => value && self.indexOf(value) === index);
    this.eventTypes = types;
  }

  normalizeEventType(type: string): string {
    if (!type) return type;
    const lower = type.toLowerCase();
    if (lower === 'concert' || lower === 'koncert') return 'Concert';
    return type;
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
      result = result.filter(e => this.normalizeEventType(e.type) === this.selectedType);
    }

    // Filter by price (null ili 0 = besplatno)
    if (this.selectedPriceFilter === 'free') {
      result = result.filter(e => e.price == null || e.price === 0);
    } else if (this.selectedPriceFilter === 'paid') {
      result = result.filter(e => e.price != null && e.price > 0);
    }

    // Filter by date (K6)
    if (this.selectedDate) {
      result = result.filter(e => {
        const eventDate = new Date(e.dateTime).toISOString().substring(0, 10);
        return eventDate === this.selectedDate;
      });
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
    this.selectedDate = '';
    this.showOnlyToday = true;
    this.applyFilters();
  }

  isFreeEvent(price: number | null | undefined): boolean {
    return price == null || price === 0;
  }

  getImageUrl(url: string): string {
    return resolveMediaUrl(url, 'https://placehold.co/400x300/667eea/ffffff?text=EVENT');
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
