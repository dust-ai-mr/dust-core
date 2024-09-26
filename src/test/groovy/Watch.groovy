import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.Cancellable
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.msgs.StopMsg
import com.mentalresonance.dust.core.msgs.Terminated
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Two Actors watch each other - the first to die kills the second.
 */
@Slf4j
class Watch extends Specification {

	static stopped = false, terminated = false

	@Slf4j
	static class Watcher extends Actor {

		Long ms
		Cancellable kill

		static Props props(Long ms) {
			return Props.create(Watcher, ms)
		}

		Watcher(Long ms) {
			this.ms = ms
		}

		@Override
		protected void preStart() {
			kill = scheduleIn(new StopMsg(), ms)
		}

		@Override
		protected void postStop() {
			kill.cancel()
		}

		@Override
		ActorBehavior createBehavior() {
			return { message ->
				switch(message) {
					case StopMsg:
						log.info "${self.path} - kill time"
						stopped = true
						stopSelf()
						break

					case Terminated:
						log.info "${self.path} - ${sender} Terminated"
						terminated = true
						stopSelf()
						break

					default:
						log.error "Got $message ??"

				}
			}
		}
	}

	/**
	 * Notice even though we take care to cancel the Stops we still get a Dead letter which is the remaining Watch going
	 * off.
	 */
	def "Suicide Pact 1"() {
		when:
			ActorSystem system = new ActorSystem("WatchTest")

			ActorRef w1 = system.context.actorOf( Watcher.props(1000), "W1")
			ActorRef w2 = system.context.actorOf( Watcher.props(2000), "W2")

			w1.watch(w2)
			w2.watch(w1)

			w1.waitForDeath()
			w2.waitForDeath()
			system.stop()
					then:
			stopped
			terminated

	}

}
