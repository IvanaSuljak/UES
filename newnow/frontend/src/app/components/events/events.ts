import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EventsService } from '../../services/events.service';

@Component({
  selector: 'app-events',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './events.html',
  styleUrls: ['./events.css']
})
export class EventsComponent implements OnInit, OnChanges {
  @Input() location!: any;
  @Input() isOpen = false;
  @Input() onClose!: () => void;

  events: any[] = [];
  isLoading = false;
  errorMessage: string | null = null;

  constructor(private eventsService: EventsService) {}

  ngOnInit(): void {
    this.checkAndLoadEvents();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['location'] && changes['location'].currentValue) {
      console.log('📍 Lokacija se promijenila:', changes['location'].currentValue);
      this.checkAndLoadEvents();
    }
  }

  private checkAndLoadEvents(): void {
    if (!this.location || !this.location.id) {
      console.warn('⚠️ Nije prosleđena ispravna lokacija u EventsComponent');
      return;
    }

    this.loadEvents();
  }

  private loadEvents(): void {
    this.isLoading = true;
    this.errorMessage = null;
    this.events = [];

    console.log(`🔄 Učitavanje događaja za lokaciju ID: ${this.location.id}`);

    this.eventsService.getEventsByLocation(this.location.id).subscribe({
      next: (data: any[]) => {
        console.log('✅ ODGOVOR IZ API-JA:', data);
        console.log('📦 Tip podataka:', typeof data);
        console.log('🔢 Broj događaja:', data?.length || 0);

        if (data && data.length > 0) {
          console.log('🎫 Prvi događaj (kompletan objekat):', data[0]);
          console.log('🔑 Ključi prvog događaja:', Object.keys(data[0]));
        }

        this.events = data || [];
        this.isLoading = false;
      },
      error: (err) => {
        console.error('❌ GREŠKA:', err);
        console.error('📋 Error status:', err.status);
        console.error('📝 Error message:', err.message);
        this.errorMessage = 'Došlo je do greške pri učitavanju događaja.';
        this.isLoading = false;
      }
    });
  }

  closeModal(): void {
    this.onClose();
  }

  onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closeModal();
    }
  }
}
