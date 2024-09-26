import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.lib.PingActor
import com.mentalresonance.dust.core.msgs.PingMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * RUns a ping/pong session but uses a future message to get interim message count. Typically
 * we can use futures to 'talk' to non-Actors - as in the case of this test script.
 *
 * Note that once the ping pong session is started we wait for a small time then send the message
 * to get the count. This is simply added to the mail box and so will get picked up somewhere mid-session.

 */
@Slf4j
@CompileStatic
class FuturePingPong extends Specification {

	static class FuturePingActor extends PingActor {

		ActorBehavior parentBehavior

		static Props props(Integer maxMessage) {
			return Props.create(FuturePingActor.class, maxMessage)
		}

		FuturePingActor(Integer maxMessage) {
			super(maxMessage)
			parentBehavior = super.createBehavior()
		}

		@Override
		protected ActorBehavior createBehavior() {

			return (message) -> {
				switch(message) {
					case FuturePingMsg:
						((FuturePingMsg) message).count.complete(remainingMessages)
						break

					default:
						parentBehavior.onMessage(message as Serializable)
				}
			}
		}

		static class FuturePingMsg implements Serializable {
			public CompletableFuture<Integer> count = new CompletableFuture<>()
		}
	}

	def "Future Ping Pong"() {
		when:
			ActorSystem system = new ActorSystem("FuturePingPong")
			log.info "Starting PingPong"
			system.context.actorOf(FuturePingActor.props(5000000), 'ping')
			system.context.actorOf(FuturePingActor.props(5000000), 'pong')
			ActorRef ping = system.context.actorSelection("/user/ping")
			ActorRef pong = system.context.actorSelection("/user/pong")
			ping.tell(new PingMsg(), pong)

			Thread.sleep(1000L)

			FuturePingActor.FuturePingMsg countMsg = new FuturePingActor.FuturePingMsg()
			ping.tell(countMsg, null)
			log.info "ping has ${countMsg.count.get()} messages remaining"

			ping.waitForDeath()
			pong.waitForDeath()
			system.stop()
					then:
			true
	}
}
