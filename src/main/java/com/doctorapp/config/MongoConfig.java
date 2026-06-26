package com.doctorapp.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.util.logging.Logger;

public class MongoConfig {
    private static final Logger LOGGER = Logger.getLogger(MongoConfig.class.getName());
    private static MongoClient mongoClient = null;
    private static final String DEFAULT_DB_NAME = "doctor_app_db";

    public static synchronized MongoClient getMongoClient() {
        if (mongoClient == null) {
            String uri = System.getenv("MONGODB_URI");
            if (uri == null || uri.isEmpty()) {
                uri = "mongodb://localhost:27017";
                LOGGER.warning("MONGODB_URI environment variable not found. Defaulting to local: " + uri);
            }
            try {
                LOGGER.info("Initializing MongoClient with URI...");
                mongoClient = MongoClients.create(uri);
                LOGGER.info("MongoClient initialized successfully.");
            } catch (Exception e) {
                LOGGER.severe("Failed to initialize MongoClient: " + e.getMessage());
                throw e;
            }
        }
        return mongoClient;
    }

    public static MongoDatabase getDatabase() {
        String uri = System.getenv("MONGODB_URI");
        String dbName = DEFAULT_DB_NAME;
        
        // Parse DB name from URI if present, e.g. mongodb://host:port/databaseName
        if (uri != null && uri.contains("/")) {
            int lastSlash = uri.lastIndexOf("/");
            if (lastSlash > uri.indexOf("://") + 2 && lastSlash < uri.length() - 1) {
                String potentialDb = uri.substring(lastSlash + 1);
                // strip query parameters if present, e.g. ?retryWrites=true
                if (potentialDb.contains("?")) {
                    potentialDb = potentialDb.substring(0, potentialDb.indexOf("?"));
                }
                if (!potentialDb.isEmpty()) {
                    dbName = potentialDb;
                }
            }
        }
        
        return getMongoClient().getDatabase(dbName);
    }

    public static void close() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
            LOGGER.info("MongoClient connection closed.");
        }
    }
}
