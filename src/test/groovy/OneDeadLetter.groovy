import com.mentalresonance.dust.core.msgs.PingMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Send a message to a nonexistent Actor. It should get logged. Delay before stopping to ensure it has
 * time to be processed
 *
 * See also 'DeadletterPubSub'
 */
@Slf4j
class OneDeadLetter extends Specification {


	def "Dead Letter"() {
		when:
			ActorSystem system = new ActorSystem("DeadLetter")
			system.context.actorSelection('/user/nothere').tell(new PingMsg(), null)
			system.stop()
					then:
			true
	}

}
