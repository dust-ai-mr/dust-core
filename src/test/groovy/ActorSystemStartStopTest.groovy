import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.msgs.StopMsg
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Start an Actor and stop it 5 seconds later. Wait by join()ing the ActorRef thread (via waitForDeath())
 */
@Slf4j
class ActorSystemStartStopTest extends Specification {

	static stopped = false


	def "ActorSystemStartStop"() {
		when:
			ActorSystem system = new ActorSystem("StopTest", 9099)
			stopped = system.stop()
		then:
			stopped
	}

}
