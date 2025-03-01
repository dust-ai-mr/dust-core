package com.mentalresonance.dust.core.actors.lib;

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.msgs.GetChildrenMsg;
import com.mentalresonance.dust.core.msgs.StartMsg;

import java.io.Serializable;
import java.util.function.Function;

import static com.mentalresonance.dust.core.actors.lib.ReaperActor.ReapMsg.ReapResponseMsg;

/**
 * A common pattern. Reap a message to all the parent's children, transform the results
 * and send that off to a client as though it came from my parent then stop. Very useful e.g. getting the
 * state of a PodManager's children and preparing that state for further delivery
 */
public class ReapTransformServiceActor extends Actor {

    ActorRef host, client;
    Class<? extends Serializable> reapingClz;
    Function<Object, Serializable> transform;

    /**
     * @param host - parent of children to whom we send the reapingMsg
     * @param client - requester - will get the result
     * @param reapingClz - class of message to be sent to children. Should have a default no arg constructor
     * @param transform - { ReapResponseMsg -> ... } value is sent to client
     * @return Props
     */
    public static Props props(ActorRef host, ActorRef client,
                                Class<? extends Serializable> reapingClz, Function<Object, Serializable> transform) {
        return Props.create(ReapTransformServiceActor.class, host, client, reapingClz, transform);
    }

    public ReapTransformServiceActor(ActorRef host, ActorRef client,
                                 Class<? extends Serializable> reapingClz, Function<Object, Serializable> transform) {
        this.host = host;
        this.client = client;
        this.reapingClz = reapingClz;
        this.transform = transform;
    }

    @Override
    protected ActorBehavior createBehavior() {
        return (Serializable message) -> {
            switch(message) {
                case StartMsg ignored:
                    host.tell(new GetChildrenMsg(), self);
                    break;

                case GetChildrenMsg msg:
                    actorOf(ReaperActor.props(10000L)).tell(
                        new ReaperActor.ReapMsg(
                            reapingClz,
                            msg.getChildren(),
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
