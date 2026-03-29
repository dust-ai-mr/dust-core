
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification
import com.mentalresonance.dust.core.actors.ActorSystemBuilder

/**
 * Start an Actor and stop it 5 seconds later. Wait by join()ing the ActorRef thread (via waitForDeath())
 */
@Slf4j
class ActorSystemStartStopTest extends Specification {

	static stopped = false


	def "ActorSystemStartStop"() {
		when:
		log.info ">>>>>>>>>>>> ActorSystemStartStop"
			ActorSystem system = new ActorSystemBuilder()
				.name("StopTest")
				.port(9099)
				.build()

			stopped = system.stop()
		then:
			stopped
	}

}
