package com.example.newnow.elasticsearch;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "locations")
@Setting(settingPath = "elasticsearch/settings.json")
public class LocationDocument {

    @Id
    private String id;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "serbian_analyzer"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String name;

    @Field(type = FieldType.Text, analyzer = "serbian_analyzer")
    private String description;

    @Field(type = FieldType.Text, analyzer = "serbian_analyzer")
    private String pdfContent;

    @Field(type = FieldType.Keyword)
    private String address;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String imageUrl;

    @Field(type = FieldType.Keyword)
    private String pdfFileName;

    @Field(type = FieldType.Integer)
    private int totalReviews;

    @Field(type = FieldType.Double)
    private double averageRating;

    @Field(type = FieldType.Double)
    private double avgPerformance;

    @Field(type = FieldType.Double)
    private double avgSoundLight;

    @Field(type = FieldType.Double)
    private double avgSpace;

    @Field(type = FieldType.Double)
    private double avgOverall;

    public LocationDocument() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPdfContent() { return pdfContent; }
    public void setPdfContent(String pdfContent) { this.pdfContent = pdfContent; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getPdfFileName() { return pdfFileName; }
    public void setPdfFileName(String pdfFileName) { this.pdfFileName = pdfFileName; }

    public int getTotalReviews() { return totalReviews; }
    public void setTotalReviews(int totalReviews) { this.totalReviews = totalReviews; }

    public double getAverageRating() { return averageRating; }
    public void setAverageRating(double averageRating) { this.averageRating = averageRating; }

    public double getAvgPerformance() { return avgPerformance; }
    public void setAvgPerformance(double avgPerformance) { this.avgPerformance = avgPerformance; }

    public double getAvgSoundLight() { return avgSoundLight; }
    public void setAvgSoundLight(double avgSoundLight) { this.avgSoundLight = avgSoundLight; }

    public double getAvgSpace() { return avgSpace; }
    public void setAvgSpace(double avgSpace) { this.avgSpace = avgSpace; }

    public double getAvgOverall() { return avgOverall; }
    public void setAvgOverall(double avgOverall) { this.avgOverall = avgOverall; }
}
