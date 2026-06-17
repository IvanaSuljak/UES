package com.example.newnow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String type; // tip mesta

    @Column(length = 1000)
    private String description;

    private String imageUrl;

    // MinIO putanja do PDF fajla sa opisom mesta
    @Column(nullable = true)
    private String pdfFileName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "manager_id")
    @JsonIgnoreProperties({"password", "email", "enabled"})
    private User manager;

    @OneToMany(mappedBy = "location", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("location")
    private List<Event> events;

    @OneToMany(mappedBy = "location", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("location")
    private List<Image> images;

    public void setId(Long id) {
        this.id = id;
    }
}