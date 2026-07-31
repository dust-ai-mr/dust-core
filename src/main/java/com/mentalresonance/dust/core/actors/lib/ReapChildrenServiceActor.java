package com.mentalresonance.dust.core.actors.lib;

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.msgs.GetChildrenMsg;
import com.mentalresonance.dust.core.msgs.ReapMsg;
import com.mentalresonance.dust.core.msgs.StartMsg;
import lombok.extern.slf4j.Slf4j;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import static com.mentalresonance.dust.core.msgs.ReapMsg.ReapResponseMsg;

/**
 * A common pattern. Reap a message to all the parent's children
 * and send that off to a client as though it came from my parent then stop. Very useful e.g. getting the
 * state of a PodManager's children and preparing that state for further delivery
 */
@Slf4j
public class ReapChildrenServiceActor extends Actor {

    ActorRef host, client;
    Serializable reapingMsg;
    Class<? extends Serializable> reapingClz;
    long timeoutMs;

    /**
     * @param host - parent of children to whom we send the reapingMsg
     * @param client - requester - will get the result
     * @param reapingMsg - non-mutable message to be sent to children.
     * @return Props
     */
    public static Props props(ActorRef host, ActorRef client, Serializable reapingMsg) {
        return Props.create(ReapChildrenServiceActor.class, host, client, reapingMsg,  null, 10000L);
    }

    /**
     * @param host - parent of children to whom we send the reapingMsg
     * @param client - requester - will get the result
     * @param reapingMsg - message to be sent to children.
     * @param timeoutMs - dead man's handle timeout - default is 10 secs
     * @return Props
     */
    public static Props props(ActorRef host, ActorRef client, Serializable reapingMsg, long timeoutMs) {
        return Props.create(ReapChildrenServiceActor.class, host, client, reapingMsg, null, timeoutMs);
    }

    /**
     *
     * @param host - parent of children to whom we send the reapingMsg
     * @param client - requester - will get the result
     * @param reapingClz - class of message to be sent to children. Should have a default no arg constructor
     * @return Props
     */
    public static Props props(ActorRef host, ActorRef client, Class<? extends Serializable> reapingClz) {
        return Props.create(ReapChildrenServiceActor.class, host, client, null, reapingClz, 10000L);
    }

    /**
     *
     * @param host - parent of children to whom we send the reapingMsg
     * @param client - requester - will get the result
     * @param reapingClz - class of message to be sent to children. Should have a default no arg constructor
     * @param timeoutMs - dead man's handle timeout
     * @return Props
     */
    public static Props props(ActorRef host, ActorRef client, Class<? extends Serializable> reapingClz, long timeoutMs) {
        return Props.create(ReapChildrenServiceActor.class, host, client, null, reapingClz, timeoutMs);
    }

    public ReapChildrenServiceActor(ActorRef host, ActorRef client, Serializable reapingMsg, Class<? extends Serializable> reapingClz, long timeoutMs) {
        this.host = host;
        this.client = client;
        this.reapingClz = reapingClz;
        this.reapingMsg = reapingMsg;
        this.timeoutMs = timeoutMs;
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
                    if (null != reapingMsg) {
                        actorOf(ReaperActor.props(timeoutMs)).tell(
                            new ReapMsg(
                                reapingMsg,
                                children,
                                ReapResponseMsg.class
                            ),
                            self
                        );
                    }
                    else if (null != reapingClz) {
                        actorOf(ReaperActor.props(timeoutMs)).tell(
                            new ReapMsg(
                                reapingClz,
                                children,
                                ReapResponseMsg.class
                            ),
                            self
                        );
                    }
                    else {
                        log.error("ReapChildrenServiceActor: reapingMsg or reapingClz must be set");
                        stopSelf();
                    }
                    break;

                case ReapResponseMsg msg:
                    client.tell(msg, parent);
                    stopSelf();
                    break;

                default: super.createBehavior().onMessage(message);
            }
        };
    }
}
