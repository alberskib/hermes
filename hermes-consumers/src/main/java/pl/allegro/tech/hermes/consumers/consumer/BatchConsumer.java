package pl.allegro.tech.hermes.consumers.consumer;

import pl.allegro.tech.hermes.api.Subscription;
import pl.allegro.tech.hermes.api.Topic;
import pl.allegro.tech.hermes.common.kafka.offset.PartitionOffset;
import pl.allegro.tech.hermes.common.metric.HermesMetrics;

import java.util.List;

public class BatchConsumer implements Consumer {
    private final Subscription subscription;
    private final Topic topic;
    private final HermesMetrics hermesMetrics;

    public BatchConsumer(Subscription subscription, Topic topic, HermesMetrics hermesMetrics) {

        this.subscription = subscription;
        this.topic = topic;
        this.hermesMetrics = hermesMetrics;
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

    @Override
    public void run() {

    }
}
