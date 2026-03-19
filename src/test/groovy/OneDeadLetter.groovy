import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.ActorSystemBuilder
import com.mentalresonance.dust.core.msgs.PingMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Get ref to a dead letter box
 * See also 'DeadletterPubSub'
 */
@Slf4j
class OneDeadLetter extends Specification {

	public static success = false

	def "Dead Letter"() {
		when:
			ActorSystem system = new ActorSystemBuilder().name("DeadLetter").build()
			ActorRef ref = system.context.actorSelection('/user/notthere')
			success = ref.isDeadLetter()
			system.stop()
		then:
			success
	}

}
