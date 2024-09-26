import com.mentalresonance.dust.core.actors.PoisonPill
import com.mentalresonance.dust.core.actors.lib.LogActor
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Create logger Actor and send message to it twice - once using ref from creation then use ref from selection.
 * Do this twice, once locally and once remotely (through dust: to myself).
 */
@Slf4j
class Path extends Specification {

	static ActorSystem system

	static class DummyMsg implements Serializable {
		String m;

		DummyMsg(String m) {
			this.m = m;
		}

		@Override
		String toString() { return m; }
	}

	def "Actor Selection to Path"() {
		when:
			log.info "Starting path test locally"
			system = new ActorSystem("Test")
			// Create instance of LogActor and get the reference from the creation (which will be /user/logger)
			var log = system.context.actorOf(LogActor.props(), "logger")
			log.tell(new DummyMsg("'From creation'"), null)

			// Now get a reference to it from ActorSelection
			var log2 = system.context.actorSelection("/user/logger")
			log2.tell(new DummyMsg("'From selection'"), null)

			// Now kill the Logger Actor.
			log2.tell(new PoisonPill(), null)
			log2.waitForDeath()

			system.stop()
		then:
			true
	}

	/*
	 * Same logic as above except we are creating the Logger in a remote ActorSystem (by giving the
	 * ActorSystem a port number)
	 */
	def "Actor Selection to Remote Path"() {
		when:
			log.info "Starting path test remotely"
			system = new ActorSystem("Path", 9099)
			var log = system.context.actorOf(LogActor.props(), "logger")
			log.tell(new DummyMsg("'From creation'"), null)

			var log2 = system.context.actorSelection("dust://localhost:9099/Test/user/logger")
			log2.tell(new DummyMsg("'From remote selection'"), null)

			/*
			 * Since we cannot waitForDeath() on remote refs at the moment (even though the Actor is running
			 * locally it was started as a remote) we simply sleep a little to give things time to complete
			 */
			system.stop()
					then:
			true
	}
}
