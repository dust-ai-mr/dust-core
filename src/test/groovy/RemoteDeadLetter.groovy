import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.msgs.DeadLetter
import com.mentalresonance.dust.core.msgs.PubSubMsg
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.msgs.StopMsg
import com.mentalresonance.dust.core.msgs.Terminated
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * An Actor sends a remote message to a non-existent Actor. This results in a dead letter in the *sender's*
 * DeadLetterActor
 */
@Slf4j
class RemoteDeadLetter extends Specification {

	public static success = false

	@Slf4j
	static class Sender extends Actor {

		static Props props() {
			return Props.create(Sender)
		}

		@Override
		protected void preStart() {
			// I want to be told about dead letters so I subscribe to be informed when a dead letter appears
			PubSubMsg sub = new PubSubMsg(DeadLetter.class as Class<Serializable>)
			// Every context has a system dead letter created for it
			context.getDeadLetterActor().tell(sub, self)
			tellSelf(new StartMsg())
		}


		@Override
		ActorBehavior createBehavior() {
			return { message ->
				switch(message) {

					case StartMsg:
						actorSelection('dust://localhost:9097/user/foo').tell(new StartMsg(), self)
						break

					case DeadLetter:
						log.info "Got local dead letter: ${message}"
						success = true
						stopSelf()
						break

					default:
						log.error "Got $message ??"

				}
			}
		}
	}

	def "RemoteDeadLetterRef"() {
		when:
			log.info ">>>>>>>>>>> RemoteDeadLetterRef"
			ActorSystem me = new ActorSystemBuilder().name("RemoteDeadLetter").port(9096).build()
			ActorSystem remoteSystem = new ActorSystemBuilder().name("RemoteDeadLetter").port(9097).build()

			ActorRef senderRef = me.context.actorOf( Sender.props(), "sender")

			senderRef.waitForDeath()
			remoteSystem.stop()
			me.stop()

		then:
			success

	}

}
