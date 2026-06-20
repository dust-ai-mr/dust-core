package com.mentalresonance.dust.core.actors.lib;

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.msgs.GetChildrenMsg;
import com.mentalresonance.dust.core.msgs.StartMsg;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.mentalresonance.dust.core.actors.lib.ReaperActor.ReapMsg.ReapResponseMsg;

/**
 * A common pattern. Reap a message to all the parent's children, transform the results
 * and send that off to a client as though it came from my parent then stop. Very useful e.g. getting the
 * state of a PodManager's children and preparing that state for further delivery
 */
@Slf4j
public class ReapTransformServiceActor extends Actor {

    ActorRef host, client;
    Class<? extends Serializable> reapingClz;
    Function<Object, Serializable> transform;
    long timeoutMs;

    /**
     * @param host - parent of children to whom we send the reapingMsg
     * @param client - requester - will get the result
     * @param reapingClz - class of message to be sent to children. Should have a default no arg constructor
     * @param transform - { ReapResponseMsg -> ... } value is sent to client
     * @return Props
     */
    public static Props props(ActorRef host, ActorRef client, Class<? extends Serializable> reapingClz,
                              Function<Object, Serializable> transform) {
        return Props.create(ReapTransformServiceActor.class, host, client, reapingClz, transform, 10000L);
    }

    /**
     * @param host - parent of children to whom we send the reapingMsg
     * @param client - requester - will get the result
     * @param reapingClz - class of message to be sent to children. Should have a default no arg constructor
     * @param transform - { ReapResponseMsg -> ... } value is sent to client
     * @param timeoutMs - dead man's handle timeout - default is 10 secs
     * @return Props
     */
    public static Props props(ActorRef host, ActorRef client, Class<? extends Serializable> reapingClz,
                              Function<Object, Serializable> transform, long timeoutMs) {
        return Props.create(ReapTransformServiceActor.class, host, client, reapingClz, transform, timeoutMs);
    }

    public ReapTransformServiceActor(ActorRef host, ActorRef client, Class<? extends Serializable> reapingClz,
                               Function<Object, Serializable> transform, long timeoutMs) {
        this.host = host;
        this.client = client;
        this.reapingClz = reapingClz;
        this.transform = transform;
        this.timeoutMs = timeoutMs;
    }

    public ReapTransformServiceActor(ActorRef host, ActorRef client, Class<? extends Serializable> reapingClz,
                                     Function<Object, Serializable> transform) {
        this(host, client, reapingClz, transform, 10000L);
    }

    @Override
    protected ActorBehavior createBehavior() {
        return (Serializable message) -> {
            switch(message) {
                case StartMsg ignored:
                    host.tell(new GetChildrenMsg(), self);
                    break;

                case GetChildrenMsg msg:
                    List<ActorRef> children = new ArrayList<>(msg.getChildren());
                    children.remove(self);
                    actorOf(ReaperActor.props(timeoutMs)).tell(
                        new ReaperActor.ReapMsg(
                            reapingClz,
                            children,
                            ReapResponseMsg.class
                        ),
                        self
                    );
                    break;

                case ReapResponseMsg msg:
                    client.tell(transform.apply(msg), parent);
                    stopSelf();
                    break;

                default: super.createBehavior().onMessage(message);
            }
        };
    }
}
