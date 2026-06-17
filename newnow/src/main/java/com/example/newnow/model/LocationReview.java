package com.example.newnow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "location_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private Integer performanceRating;

    @Column(nullable = true)
    private Integer soundLightRating;
    @Column(nullable = true)
    private Integer spaceRating;

    @Column(nullable = true)
    private Integer overallRating;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false)
    private LocalDateTime createdAt;

 //SAKRIVANJE/UKLANJANJE
    @Column(nullable = false)
    private Boolean isHidden = false;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    // event može biti null za stare utiske koji su uneseni prije Grade 8
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = true)
    @JsonIgnoreProperties({"location"})
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"password", "enabled"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    @JsonIgnoreProperties({"events", "images", "manager"})
    private Location location;

    //Koliko puta se dogadj desio u trenutku pisanja recenzije (null za stare utiske)
    @Column(nullable = true)
    private Integer eventOccurrenceCount;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.isHidden == null) this.isHidden = false;
        if (this.isDeleted == null) this.isDeleted = false;
    }

    // prosecna ocenaa
    public Double getAverageRating() {
        int count = 0;
        int sum = 0;

        if (performanceRating != null) { sum += performanceRating; count++; }
        if (soundLightRating != null) { sum += soundLightRating; count++; }
        if (spaceRating != null) { sum += spaceRating; count++; }
        if (overallRating != null) { sum += overallRating; count++; }

        return count > 0 ? (double) sum / count : 0.0;
    }
}