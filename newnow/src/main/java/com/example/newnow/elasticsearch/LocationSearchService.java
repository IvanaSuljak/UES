package com.example.newnow.elasticsearch;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.json.JsonData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LocationSearchService {

    private static final Logger logger = LogManager.getLogger(LocationSearchService.class);

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    /**
     * Napredna pretraga mesta sa svim S1 parametrima.
     * Podrzava: PhraseQuery ("), PrefixQuery (*), FuzzyQuery (~)
     * BooleanQuery AND/OR, opseg review-a i prosjecne ocjene
     */
    public List<Map<String, Object>> search(
            String nameQuery,
            String descriptionQuery,
            String pdfQuery,
            Integer reviewsMin,
            Integer reviewsMax,
            Double ratingMin,
            Double ratingMax,
            Double perfMin, Double perfMax,
            Double soundMin, Double soundMax,
            Double spaceMin, Double spaceMax,
            Double overallMin, Double overallMax,
            String operator,
            String sortBy,
            String sortOrder
    ) {
        try {
            boolean useAnd = !"OR".equalsIgnoreCase(operator);
            List<Query> textQueries = new ArrayList<>();
            List<Query> rangeQueries = new ArrayList<>();

            if (nameQuery != null && !nameQuery.isBlank())
                textQueries.add(buildTextQuery("name", nameQuery.trim()));
            if (descriptionQuery != null && !descriptionQuery.isBlank())
                textQueries.add(buildTextQuery("description", descriptionQuery.trim()));
            if (pdfQuery != null && !pdfQuery.isBlank())
                textQueries.add(buildTextQuery("pdfContent", pdfQuery.trim()));

            // Range upiti idu uvijek u MUST (AND)
            if (reviewsMin != null || reviewsMax != null)
                rangeQueries.add(buildRangeQuery("totalReviews", reviewsMin != null ? reviewsMin.doubleValue() : null, reviewsMax != null ? reviewsMax.doubleValue() : null));
            if (ratingMin != null || ratingMax != null)
                rangeQueries.add(buildRangeQuery("averageRating", ratingMin, ratingMax));
            if (perfMin != null || perfMax != null)
                rangeQueries.add(buildRangeQuery("avgPerformance", perfMin, perfMax));
            if (soundMin != null || soundMax != null)
                rangeQueries.add(buildRangeQuery("avgSoundLight", soundMin, soundMax));
            if (spaceMin != null || spaceMax != null)
                rangeQueries.add(buildRangeQuery("avgSpace", spaceMin, spaceMax));
            if (overallMin != null || overallMax != null)
                rangeQueries.add(buildRangeQuery("avgOverall", overallMin, overallMax));

            // Kreiraj BoolQuery
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

            if (textQueries.isEmpty() && rangeQueries.isEmpty()) {
                boolBuilder.must(MatchAllQuery.of(m -> m)._toQuery());
            } else {
                if (useAnd) {
                    boolBuilder.must(textQueries);
                } else {
                    if (!textQueries.isEmpty()) {
                        boolBuilder.should(textQueries);
                        boolBuilder.minimumShouldMatch("1");
                    }
                }
                boolBuilder.must(rangeQueries);
            }

            Query finalQuery = boolBuilder.build()._toQuery();

            // Highlight konfiguracija
            List<HighlightField> highlightFields = List.of(
                    new HighlightField("name"),
                    new HighlightField("description"),
                    new HighlightField("pdfContent")
            );
            HighlightParameters highlightParams = HighlightParameters.builder()
                    .withPreTags("<mark>")
                    .withPostTags("</mark>")
                    .build();
            Highlight highlight = new Highlight(highlightParams, highlightFields);
            HighlightQuery highlightQuery = new HighlightQuery(highlight, LocationDocument.class);

            // Sort
            String sf = "name.keyword";
            if ("rating".equals(sortBy)) sf = "averageRating";
            else if ("reviews".equals(sortBy)) sf = "totalReviews";
            final String sortField = sf;
            SortOrder so = "asc".equalsIgnoreCase(sortOrder) ? SortOrder.Asc : SortOrder.Desc;

            NativeQuery query = new NativeQueryBuilder()
                    .withQuery(finalQuery)
                    .withHighlightQuery(highlightQuery)
                    .withSort(s -> s.field(f -> f.field(sortField).order(so)))
                    .withPageable(PageRequest.of(0, 50))
                    .withTrackScores(true)
                    .build();

            SearchHits<LocationDocument> hits = elasticsearchOperations.search(query, LocationDocument.class);

            List<Map<String, Object>> results = new ArrayList<>();
            for (SearchHit<LocationDocument> hit : hits.getSearchHits()) {
                LocationDocument doc = hit.getContent();
                Map<String, Object> r = new HashMap<>();
                r.put("id", doc.getId());
                r.put("name", doc.getName());
                r.put("description", doc.getDescription());
                r.put("address", doc.getAddress());
                r.put("type", doc.getType());
                r.put("imageUrl", doc.getImageUrl());
                r.put("pdfFileName", doc.getPdfFileName());
                r.put("totalReviews", doc.getTotalReviews());
                r.put("averageRating", doc.getAverageRating());
                r.put("avgPerformance", doc.getAvgPerformance());
                r.put("avgSoundLight", doc.getAvgSoundLight());
                r.put("avgSpace", doc.getAvgSpace());
                r.put("avgOverall", doc.getAvgOverall());
                float scoreVal = hit.getScore();
                r.put("score", Float.isNaN(scoreVal) ? null : scoreVal);
                if (!hit.getHighlightFields().isEmpty()) {
                    r.put("highlights", hit.getHighlightFields());
                }
                results.add(r);
            }
            logger.info("ES pretraga vratila {} rezultata", results.size());
            return results;
        } catch (Exception e) {
            logger.error("Greska pri ES pretrazi: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * More Like This pretraga
     */
    public List<Map<String, Object>> moreLikeThis(String locationId) {
        try {
            Query mltQuery = MoreLikeThisQuery.of(m -> m
                    .fields(List.of("name", "description", "pdfContent"))
                    .like(l -> l.document(d -> d.index("locations").id(locationId)))
                    .minTermFreq(1)
                    .maxQueryTerms(12)
                    .minDocFreq(1)
            )._toQuery();

            NativeQuery query = new NativeQueryBuilder()
                    .withQuery(mltQuery)
                    .withPageable(PageRequest.of(0, 5))
                    .build();

            SearchHits<LocationDocument> hits = elasticsearchOperations.search(query, LocationDocument.class);

            List<Map<String, Object>> results = new ArrayList<>();
            for (SearchHit<LocationDocument> hit : hits.getSearchHits()) {
                LocationDocument doc = hit.getContent();
                if (doc.getId().equals(locationId)) continue;
                Map<String, Object> r = new HashMap<>();
                r.put("id", doc.getId());
                r.put("name", doc.getName());
                r.put("description", doc.getDescription());
                r.put("address", doc.getAddress());
                r.put("imageUrl", doc.getImageUrl());
                r.put("averageRating", doc.getAverageRating());
                results.add(r);
            }
            return results;
        } catch (Exception e) {
            logger.error("Greska pri MLT pretrazi: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Query buildTextQuery(String field, String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            String phrase = value.substring(1, value.length() - 1);
            return MatchPhraseQuery.of(m -> m.field(field).query(phrase))._toQuery();
        } else if (value.startsWith("~")) {
            String term = value.substring(1);
            return FuzzyQuery.of(f -> f.field(field).value(term).fuzziness("2"))._toQuery();
        } else if (value.endsWith("*")) {
            String prefix = value.substring(0, value.length() - 1);
            return PrefixQuery.of(p -> p.field(field + ".keyword").value(prefix).caseInsensitive(true))._toQuery();
        } else {
            return MatchQuery.of(m -> m.field(field).query(value))._toQuery();
        }
    }

    private Query buildRangeQuery(String field, Double min, Double max) {
        return Query.of(q -> q.range(r -> r
                .number(n -> {
                    n.field(field);
                    if (min != null) n.gte(min);
                    if (max != null) n.lte(max);
                    return n;
                })
        ));
    }
}
