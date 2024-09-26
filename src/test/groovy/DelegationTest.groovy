import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.msgs.NonDelegatedMsg
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.msgs.StopMsg
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * An Actor processes messages until it sees a specific trigger message whence it passes all messages,
 * except NonDelegatedMsgs to the delegatee. We stop the delegatee causing the client to send itself a StopMsg
 * which makes it stop ...
 */
@Slf4j
class DelegationTest extends Specification {

	static boolean clientMsg = false
	static boolean delegateMsg = false
	static boolean delegateStop = false
	static boolean clientStop = false
	static boolean clientND = false

	@Slf4j
	static class ClientActor extends Actor {

		static Props props() {
			Props.create(ClientActor.class)
		}

		ActorBehavior createBehavior() {
			(message) -> {
				switch(message) {
					case StartMsg:
						delegateTo(actorOf(Delegatee.props()), createBehavior(), new StopMsg())
						break

					case StopMsg:
						log.info "Client got Stop"
						clientStop = true
						stopSelf()
						break
					case NonDelegatedMsg:
						log.info "Client - Non Delegated Message: $message"
						clientND = true
						break

					default:
						clientMsg = true
						log.info "Client - Message: $message"
				}
			}
		}
	}

	@Slf4j
	static class Delegatee extends Actor {

		static Props props() {
			Props.create(Delegatee.class)
		}

		ActorBehavior createBehavior() {
			(message) -> {
				switch(message) {
					case StopMsg:
						log.info "Delegatee got stop"
						delegateStop = true
						stopSelf()
						break

					default:
						delegateMsg = true
						log.info "Delegatee - Message: $message"
				}
			}
		}
	}

	static class DoNotDelegateMsg implements NonDelegatedMsg {

		@Override
		String toString() { 'Hello Again !!' }
	}

	def "Delegate"() {
		when:
			ActorSystem system = new ActorSystem("DelegateTest")
			ActorRef client = system.context.actorOf(ClientActor.props(), 'client')

			client.tell("Hello", null)  // client
			client.tell("World", null)  // client
			client.tell(new StartMsg(), null) // client delegates
			client.tell("'I Should be handled by delegatee'", null) // delegated
			client.tell(new DoNotDelegateMsg(), null) // not delegated
			client.tell(new StopMsg(), null) // delegatee stops - client switches to regular behavior and sends itself a StopMsg
			client.waitForDeath()
			system.stop()
		then:
			clientMsg
			delegateMsg
			clientStop
			delegateStop
			clientND
	}
}
