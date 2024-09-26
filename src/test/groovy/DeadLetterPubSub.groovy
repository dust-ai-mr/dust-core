import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.msgs.PingMsg
import com.mentalresonance.dust.core.msgs.PubSubMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.msgs.DeadLetter
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Create an actor which subscribes to dead letters, then send a dead letter.
 */
@Slf4j
class DeadLetterPubSub extends Specification {

	public static success = false

	@Slf4j
	/*
	  A subscriber to dead letters
	 */
	static class Subscriber extends Actor {
		static Props props() {
			Props.create(Subscriber)
		}

		@Override
		protected void preStart() {
			// I want to be told about dead letters so I subscribe to be informed when a dead letter appears
			PubSubMsg sub = new PubSubMsg(DeadLetter.class as Class<Serializable>)
			// Every context has a system dead letter created for it
			context.getDeadLetterActor().tell(sub, self)
		}

		@Override
		ActorBehavior createBehavior() {
			(message) -> {
				log.info "Subscribed to dead letters - got ${message}"
				success = true
				/*
				 	Stop myself. The DeadLetter Actor will have been Watch()ing me and so will receive a Terminated
				 	message when I die - causing it to unsubscribe me. (Not that that matters - the test will immediately
				 	stop anyway).
				 */
				stopSelf()
			}
		}
	}

	def "Dead Letter Pub Sub"() {
		when:
			ActorSystem system = new ActorSystem("DeadLetterPubSub")
			// Create a subscriber - give it time to subscribe
			ActorRef subscriberRef = system.context.actorOf(Subscriber.props(), "subscriber")
			Thread.sleep(500L)
			// Now send a message to a non-existent Actor
			system.context.actorSelection('/user/nothere').tell(new PingMsg(), null)
			// After the subscriber receives the dead letter PingMsg  it will stop
			subscriberRef.waitForDeath()
			system.stop()
					then:
			success
	}

}
