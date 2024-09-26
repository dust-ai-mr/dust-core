import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Use a specific trait (TestTrait which implements callMe()) to extend
 */
@Slf4j
class TraitTest extends Specification {

	@Slf4j
	static class Supervisor extends Actor implements TestTrait {

		static Props props() {
			return Props.create(Supervisor)
		}

		@Override
		protected void preStart() {
			callMe()
		}
	}

	def "Trait Test"() {
		when:
			ActorSystem system = new ActorSystem("TraitTest")
			system.context.actorOf( Supervisor.props(), "trait")
			system.stop()
					then:
			true
	}

}
