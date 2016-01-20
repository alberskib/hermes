package pl.allegro.tech.hermes.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.assertj.core.api.Condition;
import org.assertj.core.api.StrictAssertions;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import pl.allegro.tech.hermes.api.ContentType;
import pl.allegro.tech.hermes.api.DeliveryType;
import pl.allegro.tech.hermes.api.EndpointAddress;
import pl.allegro.tech.hermes.api.Subscription;
import pl.allegro.tech.hermes.api.SubscriptionPolicy;
import pl.allegro.tech.hermes.api.Topic;
import pl.allegro.tech.hermes.integration.env.SharedServices;
import pl.allegro.tech.hermes.integration.helper.Assertions;
import pl.allegro.tech.hermes.test.helper.endpoint.RemoteServiceEndpoint;
import pl.allegro.tech.hermes.test.helper.message.TestMessage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static javax.ws.rs.core.Response.Status.CREATED;
import static pl.allegro.tech.hermes.api.Subscription.Builder.subscription;
import static pl.allegro.tech.hermes.api.SubscriptionPolicy.Builder.subscriptionPolicy;
import static pl.allegro.tech.hermes.integration.test.HermesAssertions.assertThat;

public class BatchDeliveryTest extends IntegrationTest {
    private RemoteServiceEndpoint remoteService;

    private ObjectMapper mapper = new ObjectMapper();

    @BeforeMethod
    public void initializeAlways() {
        this.remoteService = new RemoteServiceEndpoint(SharedServices.services().serviceMock());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldDeliverMessagesInBatch() throws IOException {
        // given
        Topic topic = operations.buildTopic("batchSizeTest", "topic");

        SubscriptionPolicy policy = subscriptionPolicy()
                .applyDefaults()
                .withDeliveryType(DeliveryType.BATCH)
                .withBatchSize(2)
                .withBatchTime(Integer.MAX_VALUE)
                .build();

        Subscription subscription = subscription()
                .applyDefaults()
                .withContentType(ContentType.JSON)
                .withSupportTeam("foo")
                .withSubscriptionPolicy(policy)
                .withName("batchSizeTest")
                .withTopicName(topic.getQualifiedName())
                .withEndpoint(new EndpointAddress(HTTP_ENDPOINT_URL))
                .build();

        operations.createSubscription(topic, subscription);

        TestMessage[] testMessages = TestMessage.simpleMessages(policy.getBatchSize());
        remoteService.expectMessages(testMessages);

        // when
        Arrays.stream(testMessages).forEach( m ->
            assertThat(publisher.publish(topic.getQualifiedName(), m.body())).hasStatus(CREATED)
        );

        // then
        remoteService.waitUntilReceived(5, 1, message -> {
            try {
                assertThat(mapper.readValue(message, List.class)).hasSize(testMessages.length);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
