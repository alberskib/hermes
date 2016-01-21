package pl.allegro.tech.hermes.consumers.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.allegro.tech.hermes.api.Subscription;
import pl.allegro.tech.hermes.api.Topic;
import pl.allegro.tech.hermes.common.kafka.offset.PartitionOffset;
import pl.allegro.tech.hermes.common.metric.HermesMetrics;
import pl.allegro.tech.hermes.consumers.consumer.receiver.MessageReceiver;

import java.util.List;

public class BatchConsumer implements Consumer {
    private static final Logger logger = LoggerFactory.getLogger(BatchConsumer.class);

    private final MessageReceiver messageReceiver;
    private final Subscription subscription;
    private final Topic topic;
    private final HermesMetrics hermesMetrics;

    boolean consuming = true;

    public BatchConsumer(MessageReceiver messageReceiver, Subscription subscription, Topic topic, HermesMetrics hermesMetrics) {
        this.messageReceiver = messageReceiver;
        this.subscription = subscription;
        this.topic = topic;
        this.hermesMetrics = hermesMetrics;
    }

    @Override
    public void run() {
        setThreadName();
        logger.info("Stopped consumer for subscription {}", subscription.getId());
        unsetThreadName();
    }

    @Override
    public Subscription getSubscription() {
        return subscription;
    }

    @Override
    public void updateSubscription(Subscription modifiedSubscription) {

    }

    @Override
    public void stopConsuming() {
        logger.info("Stopping consumer for subscription {}", subscription.getId());
        consuming = false;
    }

    @Override
    public void waitUntilStopped() throws InterruptedException {

    }

    @Override
    public List<PartitionOffset> getOffsetsToCommit() {
        return null;
    }

    @Override
    public boolean isConsuming() {
        return false;
    }
}
