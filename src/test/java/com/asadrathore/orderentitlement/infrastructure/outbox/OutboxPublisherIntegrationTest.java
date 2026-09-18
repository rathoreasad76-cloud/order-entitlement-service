package com.asadrathore.orderentitlement.infrastructure.outbox;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the publisher actually talks to SNS, not just that it compiles.
 *
 * <p>Writes an outbox row directly (bypassing the HTTP layer, since this test is
 * about the publisher, not order placement), subscribes a real SQS queue to the
 * topic the app publishes to, and waits for the message to show up with the
 * message attributes intact. Both Postgres and LocalStack run as real containers
 * via Testcontainers — no mocking of the AWS SDK.
 */
@SpringBootTest
@Testcontainers
class OutboxPublisherIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_entitlement")
            .withUsername("order_entitlement")
            .withPassword("order_entitlement");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7"))
            .withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

    private static String topicArn;
    private static String queueUrl;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("aws.endpoint-override", () -> localstack.getEndpoint().toString());
        registry.add("aws.region", localstack::getRegion);
        registry.add("aws.access-key", localstack::getAccessKey);
        registry.add("aws.secret-key", localstack::getSecretKey);
        registry.add("aws.sns.topic-arn", () -> topicArn);
    }

    @BeforeAll
    static void createTopicAndQueue() {
        SnsClient sns = SnsClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();

        SqsClient sqs = buildSqsClient();

        topicArn = sns.createTopic(b -> b.name("order-entitlement-events")).topicArn();

        queueUrl = sqs.createQueue(CreateQueueRequest.builder().queueName("test-subscriber-queue").build()).queueUrl();
        String queueArn = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .attributes().get(QueueAttributeName.QUEUE_ARN);

        sns.subscribe(b -> b.topicArn(topicArn).protocol("sqs").endpoint(queueArn));
    }

    private static SqsClient buildSqsClient() {
        return SqsClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
    }

    @Autowired
    private OutboxEventWriter outboxEventWriter;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Test
    void publishedOutboxEventArrivesOnTheSubscribedQueueWithMessageAttributes() {
        outboxEventWriter.write("EntitlementGranted", "customer-77",
                Map.of("customerId", "customer-77", "productCode", "PRO_LICENSE", "grantedAt", Instant.now().toString()));

        outboxPublisher.publishPending();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            SqsClient sqs = buildSqsClient();
            List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageAttributeNames("All")
                            .waitTimeSeconds(2)
                            .build())
                    .messages();

            assertThat(messages).isNotEmpty();
            Message message = messages.get(0);
            assertThat(message.body()).contains("customer-77").contains("PRO_LICENSE");
        });
    }
}
