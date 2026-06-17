package com.example.newnow.elasticsearch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchInitializer {

    private static final Logger logger = LogManager.getLogger(ElasticsearchInitializer.class);

    @Autowired
    private LocationIndexService indexService;

    @Autowired
    private com.example.newnow.service.MinioService minioService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeOnStartup() {
        try {
            // Inicijalizuj MinIO bucket
            minioService.ensureBucketExists();
            logger.info("MinIO bucket inicijalizovan");
        } catch (Exception e) {
            logger.warn("MinIO nije dostupan pri startu: {}", e.getMessage());
        }

        try {
            // Reindeksiraj sve lokacije u Elasticsearch
            Thread.sleep(2000); // kratko cekanje da ES bude spreman
            indexService.reindexAll();
            logger.info("Elasticsearch indeksiranje lokacija zavrseno");
        } catch (Exception e) {
            logger.warn("Elasticsearch nije dostupan pri startu: {}", e.getMessage());
        }
    }
}
