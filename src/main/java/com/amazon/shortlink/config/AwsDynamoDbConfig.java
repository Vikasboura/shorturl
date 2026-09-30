package com.amazon.shortlink.config;

import com.amazon.shortlink.domain.ClickEvent;
import com.amazon.shortlink.domain.ShortUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

/**
 * AWS DynamoDB Configuration.
 * 
 * In AWS production: Uses DefaultCredentialsProvider and IAM Role execution.
 * In LocalStack / Local test: Overrides endpoint with http://localhost:4566 and dummy credentials.
 */
@Configuration
@ConditionalOnProperty(name = "aws.dynamodb.mode", havingValue = "dynamodb")
public class AwsDynamoDbConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;

    @Value("${aws.dynamodb.urls-table-name:shortlink-urls}")
    private String urlsTableName;

    @Value("${aws.dynamodb.clicks-table-name:shortlink-clicks}")
    private String clicksTableName;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .httpClient(UrlConnectionHttpClient.builder().build());

        if (dynamoDbEndpoint != null && !dynamoDbEndpoint.trim().isEmpty()) {
            builder.endpointOverride(URI.create(dynamoDbEndpoint.trim()))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<ShortUrl> shortUrlTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(urlsTableName, TableSchema.fromBean(ShortUrl.class));
    }

    @Bean
    public DynamoDbTable<ClickEvent> clickEventTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(clicksTableName, TableSchema.fromBean(ClickEvent.class));
    }
}
