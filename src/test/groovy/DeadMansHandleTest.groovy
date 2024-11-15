import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Start a dead man's handle and let it drop
 */
@Slf4j
class DeadMansHandleTest extends Specification {

	static handled = false

	@Slf4j
	static class DeadMansHandleTestActor extends Actor {

		static Props props() {
			Props.create(DeadMansHandleTestActor.class)
		}

		void preStart() {
			dieIn(2000L)
			tellSelf(new StartMsg())
		}

		@Override
		void dying() {
			log.info "Handle dropped ....."
			handled = true
		}

		ActorBehavior createBehavior() {
			(message) -> {
				switch(message) {
					case StartMsg:
						log.info "Got start - waiting for handle drop"
						safeSleep(200L)
						tellSelf(message)
				}
			}
		}
	}

	def "DeadMansHandle" () {
		given:
			ActorSystem system = new ActorSystem("DeadMansHandle")
			system.context.actorOf(DeadMansHandleTestActor.props(), 'test').waitForDeath()
			system.stop()
					expect:
			handled == true
	}
}
