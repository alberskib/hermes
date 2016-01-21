package pl.allegro.tech.hermes.consumers.consumer;

import pl.allegro.tech.hermes.api.Subscription;
import pl.allegro.tech.hermes.common.kafka.offset.PartitionOffset;

import java.util.List;

public interface Consumer extends Runnable {

    Subscription getSubscription();

    void updateSubscription(Subscription modifiedSubscription);

    void stopConsuming();

    void waitUntilStopped() throws InterruptedException;

    List<PartitionOffset> getOffsetsToCommit();

    boolean isConsuming();
}
