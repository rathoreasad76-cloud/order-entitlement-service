package com.asadrathore.orderentitlement.infrastructure.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;

import java.net.URI;

/**
 * Builds the SNS client used to publish outbox events.
 *
 * <p>{@code aws.endpoint-override} is what makes this point at LocalStack instead of
 * real AWS during local development and tests — when it's unset, the SDK falls back
 * to its normal endpoint resolution and standard credential chain (IAM role, env
 * vars, etc.), so this same code path is what would run against a real topic in
 * production. Nothing about the publishing logic changes between the two; only
 * where the client is told to connect.
 */
@Configuration
public class AwsClientConfig {

    @Bean
    public SnsClient snsClient(
            @Value("${aws.region:us-east-1}") String region,
            @Value("${aws.endpoint-override:}") String endpointOverride,
            @Value("${aws.access-key:test}") String accessKey,
            @Value("${aws.secret-key:test}") String secretKey) {

        SnsClientBuilder builder = SnsClient.builder().region(Region.of(region));

        if (!endpointOverride.isBlank()) {
            // LocalStack (or any non-default endpoint) — also needs static
            // dummy credentials since there's no real IAM behind it.
            builder = builder
                    .endpointOverride(URI.create(endpointOverride))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        }

        return builder.build();
    }
}
