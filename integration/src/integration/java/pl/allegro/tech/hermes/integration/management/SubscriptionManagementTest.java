package pl.allegro.tech.hermes.integration.management;

import org.assertj.core.api.Assertions;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import pl.allegro.tech.hermes.api.ContentType;
import pl.allegro.tech.hermes.api.EndpointAddress;
import pl.allegro.tech.hermes.api.Subscription;
import pl.allegro.tech.hermes.api.Topic;
import pl.allegro.tech.hermes.client.HermesClient;
import pl.allegro.tech.hermes.client.jersey.JerseyHermesSender;
import pl.allegro.tech.hermes.common.config.Configs;
import pl.allegro.tech.hermes.integration.IntegrationTest;
import pl.allegro.tech.hermes.integration.env.SharedServices;
import pl.allegro.tech.hermes.test.helper.endpoint.RemoteServiceEndpoint;
import pl.allegro.tech.hermes.test.helper.message.TestMessage;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.jayway.awaitility.Awaitility.await;
import static java.net.URI.create;
import static javax.ws.rs.client.ClientBuilder.newClient;
import static pl.allegro.tech.hermes.api.Subscription.Builder.subscription;
import static pl.allegro.tech.hermes.api.SubscriptionPolicy.Builder.subscriptionPolicy;
import static pl.allegro.tech.hermes.api.Topic.Builder.topic;
import static pl.allegro.tech.hermes.client.HermesClientBuilder.hermesClient;
import static pl.allegro.tech.hermes.integration.test.HermesAssertions.assertThat;

public class SubscriptionManagementTest extends IntegrationTest {

    public static final TestMessage MESSAGE = TestMessage.of("hello", "world");

    private RemoteServiceEndpoint remoteService;
    private HermesClient client;

    @BeforeMethod
    public void initializeAlways() {
        remoteService = new RemoteServiceEndpoint(SharedServices.services().serviceMock());
        client = hermesClient(new JerseyHermesSender(newClient())).withURI(create("http://localhost:" + FRONTEND_PORT)).build();
    }

    @Test
    public void shouldCreateSubscriptionWithActiveStatus() {
        // given
        Topic topic = operations.buildTopic("subscribeGroup", "topic");

        // when
        Response response = management.subscription().create(
                topic.getQualifiedName(),
                subscription().withName("subscription").withEndpoint(EndpointAddress.of("http://whatever.com"))
                        .withSupportTeam("team")
                        .applyDefaults().build()
        );

        // then
        assertThat(response).hasStatus(Response.Status.CREATED);
        wait.untilSubscriptionCreated(topic, "subscription", false);
        assertThat(management.subscription().list(topic.getQualifiedName(), false)).containsExactly("subscription");
        wait.untilSubscriptionIsActivated(topic, "subscription");
    }

    @Test
    public void shouldSuspendSubscription() {
        // given
        Topic topic = operations.buildTopic("suspendSubscriptionGroup", "topic");
        operations.createSubscription(topic, "subscription", HTTP_ENDPOINT_URL);
        wait.untilSubscriptionIsActivated(topic, "subscription");

        // when
        Response response = management.subscription().updateState(
                topic.getQualifiedName(),
                "subscription", Subscription.State.SUSPENDED);

        // then
        assertThat(response).hasStatus(Response.Status.OK);
        wait.untilSubscriptionIsSuspended(topic, "subscription");
    }

    @Test
    public void shouldUpdateSubscriptionEndpoint() throws Throwable {
        //given
        Topic topic = operations.buildTopic("updateSubscriptionEndpointAddressGroup", "topic");
        operations.createSubscription(topic, "subscription", HTTP_ENDPOINT_URL + "v1/");
        wait.untilSubscriptionIsActivated(topic, "subscription");

        // when
        Response response = management.subscription().update(
                topic.getQualifiedName(),
                "subscription", subscription().withName("subscription").withTopicName(topic.getQualifiedName()).withEndpoint(EndpointAddress.of(HTTP_ENDPOINT_URL)).build());

        // then
        assertThat(response).hasStatus(Response.Status.OK);
        wait.untilSubscriptionEndpointAddressChanged(topic, "subscription", EndpointAddress.of(HTTP_ENDPOINT_URL));
        wait.untilSubscriptionIsActivated(topic, "subscription");

        remoteService.expectMessages(MESSAGE.body());
        publishMessage(topic.getQualifiedName(), MESSAGE.body());
        remoteService.waitUntilReceived();
    }

    @Test
    public void shouldRemoveSubscription() {
        // given
        Topic topic = operations.buildTopic("removeSubscriptionGroup", "topic");
        operations.createSubscription(topic, "subscription", HTTP_ENDPOINT_URL);

        // when
        Response response = management.subscription().remove(topic.getQualifiedName(), "subscription");

        // then
        assertThat(response).hasStatus(Response.Status.OK);
        assertThat(management.subscription().list(topic.getQualifiedName(), false)).doesNotContain(
                "subscription");
    }

    @Test
    public void shouldGetEventStatus() throws InterruptedException {
        // given
        Topic topic = operations.buildTopic(topic().withName("eventStatus", "topic").withContentType(ContentType.JSON).withTrackingEnabled(true).build());

        Subscription subscription = subscription().withName("subscription")
                .withEndpoint(EndpointAddress.of(HTTP_ENDPOINT_URL))
                .withTrackingEnabled(true)
                .withSupportTeam("team")
                .withSubscriptionPolicy(subscriptionPolicy().applyDefaults().build())
                .build();

        operations.createSubscription(topic, subscription);
        remoteService.expectMessages(MESSAGE.body());

        // when
        String messageId = publishMessage(topic.getQualifiedName(), MESSAGE.body());
        remoteService.waitUntilReceived();

        // then
        await().atMost(30, TimeUnit.SECONDS).until(() -> getMessageTrace(topic.getQualifiedName(), "subscription", messageId).size() == 3);

        List<Map<String, String>> traces = getMessageTrace(topic.getQualifiedName(), "subscription", messageId);
        traces.forEach(entry -> assertThat(entry).containsEntry("messageId", messageId));
        assertThat(traces.get(0)).containsEntry("status", "SUCCESS").containsKey("cluster");
        assertThat(traces.get(1)).containsEntry("status", "INFLIGHT").containsKey("cluster");
        assertThat(traces.get(2)).containsEntry("status", "SUCCESS").containsKey("cluster");
        traces.forEach(trace -> assertThat(trace).containsEntry("cluster", Configs.KAFKA_CLUSTER_NAME.getDefaultValue()));
    }

    @Test
    public void shouldReturnSubscriptionsThatAreCurrentlyTrackedForGivenTopic() {
        // given
        Topic topic = operations.buildTopic("tracked", "topic");
        Subscription subscription = subscription()
                .withName("sub")
                .withTopicName(topic.getName())
                .withEndpoint(new EndpointAddress(HTTP_ENDPOINT_URL))
                .withSupportTeam("team")
                .withTrackingEnabled(true).build();
        operations.createSubscription(topic, subscription);
        operations.createSubscription(topic, "sub2", HTTP_ENDPOINT_URL);

        // when
        List<String> tracked = management.subscription().list(topic.getQualifiedName(), true);

        // then
        assertThat(tracked).containsExactly(subscription.getName());
    }

    @Test
    public void shouldReturnsTrackedAndNotSuspendedSubscriptionsForGivenTopic() {

        // given
        Topic topic = operations.buildTopic("queried", "topic");
        Subscription subscription1 = createSubscription(topic, "sub1", true);
        Subscription subscription2 = createSubscription(topic, "sub2", true);
        operations.createSubscription(topic, subscription1);
        operations.createSubscription(topic, subscription2);
        operations.createSubscription(topic, "sub3", HTTP_ENDPOINT_URL);
        operations.suspendSubscription(topic, subscription2.getName());

        // and
        String query = "{\"query\": {\"trackingEnabled\": \"true\", \"state\": {\"ne\": \"SUSPENDED\"}}}";

        // when
        List<String> tracked = management.subscription().queryList(topic.getQualifiedName(), query);

        // then
        Assertions.assertThat(tracked).containsExactly(subscription1.getName());
    }

    @Test
    public void shouldNotAllowSubscriptionNameToContainDollarSign() {
        // given
        Topic topic = operations.buildTopic("dollar", "topic");

        Stream.of("$name", "na$me", "name$").forEach(name -> {
            // when
            Response response = management.subscription()
                    .create(topic.getQualifiedName(), subscription()
                            .withName(name)
                            .withTopicName(topic.getName())
                            .withSupportTeam("team")
                            .withEndpoint(new EndpointAddress(HTTP_ENDPOINT_URL)).build());

            // then
            assertThat(response).hasStatus(Response.Status.BAD_REQUEST);
        });
    }

    private List<Map<String, String>> getMessageTrace(String topic, String subscription, String messageId) {
        Response response = management.subscription().getMessageTrace(topic, subscription, messageId);
        return response.readEntity(new GenericType<List<Map<String, String>>>() {
        });
    }

    private String publishMessage(String topic, String body) {
        return client.publish(topic, body).join().getMessageId();
    }

    private Subscription createSubscription(Topic topic, String name, boolean tracked) {
        return subscription()
                .withName(name)
                .withTopicName(topic.getName())
                .withEndpoint(new EndpointAddress(HTTP_ENDPOINT_URL))
                .withSupportTeam("team")
                .withTrackingEnabled(tracked).build();
    }
}
