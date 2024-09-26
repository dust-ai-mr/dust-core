import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.msgs.StopMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Start an Actor and stop it 5 seconds later. Wait by join()ing the ActorRef thread (via waitForDeath())
 */
@Slf4j
class StopTest extends Specification {

	static stopped = false

	static class StopActor extends Actor {

		static Props props() {
			Props.create(StopActor.class)
		}

		void preStart() {
			scheduleIn(new StopMsg(), 5000)
		}

		StopActor() {}

		ActorBehavior createBehavior() {
			(message) -> {
				stopped = true
				stopSelf()
			}
		}
	}

	def "Stop"() {
		when:
			ActorSystem system = new ActorSystem("StopTest")
			log.info "Starting ...."
			system.context.actorOf(StopActor.props(), 'test').waitForDeath();
			log.info "Stopped"
			system.stop()
					then:
			stopped
	}

}
