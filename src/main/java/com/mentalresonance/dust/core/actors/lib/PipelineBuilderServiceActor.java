package com.mentalresonance.dust.core.actors.lib;

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.msgs.StartMsg;
import com.mentalresonance.dust.core.msgs.StopMsg;
import lombok.Getter;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * Elementary Actor to build a pair of List<Props>, List<Names> and send a Pipeline props
 * constructed using this pair back to the requester. The requester can then build a pipeline from this.
 * The sequence of messages to be sent here by a client is StartMsg(), StageMsg() .. StageMsg(), StopMsg()
 * We don't build the pipeline here since we are a services Actor and our child would die.
 * This allows the dynamic creation of pipelines - very handy for Agentic systems and LLMs ...
 */
public class PipelineBuilderServiceActor extends Actor {

    List<Props> props = new LinkedList<>();
    List<String> names = new LinkedList<>();
    ActorRef originalSender;

    public static Props props() { return Props.create(PipelineBuilderServiceActor.class); }

    @Override
    protected ActorBehavior createBehavior() {
        return (Serializable message) -> {
            switch(message) {
                case StartMsg ignored:
                    originalSender = sender;
                    break;

                case StopMsg ignored:
                    originalSender.tell(new PipelineStagesMsg(props, names), self);
                    stopSelf();
                    break;

                case StageMsg msg:
                    props.add(msg.props);
                    names.add(msg.name);
                    break;

                default:
                    throw new IllegalStateException("Unexpected value: " + message);
            }
        };
    }

    @Getter
    public static class PipelineStagesMsg implements Serializable {
        Props pipelineProps;

        public PipelineStagesMsg(List<Props> props, List<String> names) {
            pipelineProps = PipelineActor.props(props, names);
        }
    }

    @Getter
    public static class StageMsg implements Serializable {
        Props props;
        String name;

        public StageMsg(Props props, String name) {
            this.props = props;
            this.name = name;
        }
    }
}
