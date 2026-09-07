package br.com.talk.a2a.spring;

import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;

/**
 * Push notifications nao fazem parte do roteiro da palestra; este sender existe apenas
 * para satisfazer o {@code MainEventBusProcessor} do SDK.
 */
public class NoOpPushNotificationSender implements PushNotificationSender {

    @Override
    public void sendNotification(StreamingEventKind event, Task taskSnapshot) {
        // no-op
    }
}
