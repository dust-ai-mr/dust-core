import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.lib.ReaperActor
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.actors.lib.ReaperActor.ReapMsg
import com.mentalresonance.dust.core.actors.lib.ReaperActor.ReapMsg.ReapResponseMsg
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * A Parent Actor creates a number of children whose response to a StartMsg is to send the sender a Random integer.
 * The Parent Actor itself responds to a StartMsg from a Client Actor by creating a ReaperActor which will send a StartMsg to these children.
 *
 *
 */
@Slf4j
class ReapTest extends Specification {

	static complete

	@Slf4j
	static class ClientActor extends Actor {

		static Props props() {
			Props.create(ClientActor.class)
		}

		void preStart() {
			log.info "Created ${self.path}"
			actorSelection('/user/parent').tell(new StartMsg(), self)
		}

		ActorBehavior createBehavior() {
			(message) -> {
				switch(message) {
					case ReapResponseMsg:
						ReapResponseMsg rrm = (ReapResponseMsg)message
						complete = rrm.complete
						log.info ">>> ${rrm.results}"
						log.info "Reap was ${complete ? '' : 'not '}completed"
						stopSelf()
						break

					default:
						log.warn "Client - Unexpected Message: $message"
				}
			}
		}
	}

	@Slf4j
	static class ParentActor extends Actor {

		static Props props() {
			Props.create(ParentActor.class)
		}

		void preStart() {
			log.info "Created ${self.path}"
			(1..10).each {
				actorOf(ChildActor.props(), "child$it")
			}
		}

		ActorBehavior createBehavior() {
			(message) -> {
				switch(message) {
					case StartMsg:
						ReapMsg reapMsg = new ReapMsg(
							StartMsg.class, // New instances of StartMsg will be sent to ..
							children.toList() // my children by teh ReaperActor
						)
						// Proxy as though from sender so it will get ReapResponse
						actorOf(ReaperActor.props(10000L)).tell(reapMsg, sender)
						break

					default:
						log.warn "Parent - Unexpected Message: $message"
				}
			}
		}

		@Slf4j
		static class ChildActor extends Actor {
			static Props props() {
				Props.create(ChildActor.class)
			}

			@Override
			void preStart() {
				log.info "Created ${self.path}"
			}

			@Override
			ActorBehavior createBehavior() {
				(message) -> {
					switch(message) {
						case StartMsg:
							sender.tell(new Random().nextInt(), self)
							break

						default:
							log.warn "Child - Unexpected Message: $message"
					}
				}
			}
		}
	}

	def "Reap"() {
		when:
			ActorSystem system = new ActorSystem("ReapTest")
			system.context.actorOf(ParentActor.props(), 'parent')
			system.context.actorOf(ClientActor.props(), 'client').waitForDeath();
			system.stop()
					then:
			complete
	}

}
