import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.msgs.StartMsg
import com.mentalresonance.dust.core.msgs.StopMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification

/**
 * Start a dead mans handle but stop gracefully before it gets dropped - so the 'Handle drop message should not be seen'
 */
@Slf4j
class DeadMansHandleCancelTest extends Specification {

	static stopped = false

	@Slf4j
	static class DeadMansHandleTestActor extends Actor {

		static Props props() {
			Props.create(DeadMansHandleTestActor.class)
		}

		void preStart() {
			dieIn(2000L) // Drop handle in 2 seconds
			scheduleIn(new StopMsg(), 1000L) // but stop myself in 1
			tellSelf(new StartMsg())
		}

		@Override
		/*
		  Should not be called. The test fails if it is.
		 */
		void dying() {
			log.info "Handle drop ....."
			stopped = false
		}

		ActorBehavior createBehavior() {
			(message) -> {
				switch(message) {
					case StartMsg:
						log.info "Got start .. waiting to drop handle or be stopped .."
						safeSleep(200L)
						tellSelf(message)
						break

					case StopMsg:
						cancelDeadMansHandle()
						log.info "Stopped"
						stopped = true
						cancelDeadMansHandle()
						stopSelf()
				}
			}
		}
	}

	def "DeadMansHandleCancel" () {
		when:
			ActorSystem system = new ActorSystem("DeadMansHandleCancel")
			system.context.actorOf(DeadMansHandleTestActor.props(), 'test').waitForDeath()
			system.stop()
					then:
			stopped
	}
}
