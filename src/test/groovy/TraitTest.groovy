import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorTrait
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Use a specific trait (TestTrait which implements callMe()) to extend
 */
@Slf4j
class TraitTest extends Specification {

	static boolean called = false

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

	static interface TestTrait extends ActorTrait {

		default void callMe() {
			System.out.println(String.format("MyActorRef - %s - MyActor %s", getSelf(), this))
			called = true
		}
	}

	def "Trait Test"() {
		when:
			ActorSystem system = new ActorSystem("TraitTest")
			system.context.actorOf( Supervisor.props(), "trait")
			Thread.sleep(500)
			system.stop()
		then:
			called
	}

}
