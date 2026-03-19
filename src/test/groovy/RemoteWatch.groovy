import com.mentalresonance.dust.core.actors.*
import com.mentalresonance.dust.core.msgs.StopMsg
import com.mentalresonance.dust.core.msgs.Terminated
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * A local Actor watches a remote Actor and waits for death
 */
@Slf4j
class RemoteWatch extends Specification {

	public static terminated = false

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

					case Terminated:
						log.info "Stopping ${self.path} - ${sender} is Terminated"
						terminated = true
						stopSelf()
						break

					default:
						log.error "Got $message ??"

				}
			}
		}
	}

	def "Suicide Pact 2"() {
		when:
			ActorSystem system = new ActorSystemBuilder().name("WatchTest2").port(9096).build()
			ActorSystem remoteSystem = new ActorSystemBuilder().name("RemoteWatchTest2").port(9000).build()

			ActorRef w1 = remoteSystem.context.actorOf( Watcher.props(1000), "W1")
			ActorRef w2 = system.context.actorOf(Watcher.props(1000), "W2")

			w2.watch(w1)

			system.context.stop(w1)
			w2.waitForDeath()
		then:
			terminated

	}

}
