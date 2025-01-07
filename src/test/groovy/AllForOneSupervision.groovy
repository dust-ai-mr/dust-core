import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.PoisonPill
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.actors.SupervisionStrategy
import com.mentalresonance.dust.core.msgs.ChildExceptionMsg
import com.mentalresonance.dust.core.msgs.StopMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import groovy.util.logging.Slf4j
import spock.lang.Specification
import static com.mentalresonance.dust.core.actors.SupervisionStrategy.*

/**
 * All for one supervision .. fire up a number of Actors - one of which throws. We then test out stop, restart and
 * resume strategies.
 */
@Slf4j
class AllForOneSupervision extends Specification {

	ActorSystem system = new ActorSystem("AllForOneSupervision")

	public static boolean stopped = false, restarted = false, resumed = false

	@Slf4j
	/*
	  This Actor will create two Actors but send one a message telling it to throw an exception. It then
	  receives a ChildExceptionMsg from this erroring Actor and handles the error according to
	  the strategy and mode configuration parameters
	 */
	static class Supervisor extends Actor {

		static Props props(int strategy, int mode) {
			return Props.create(Supervisor, strategy, mode)
		}

		Supervisor(int strategy, int mode) {
			supervisor = new SupervisionStrategy(strategy, mode)
		}

		@Override
		protected void preStart() {
			ActorRef badboyRef = actorOf(Child.props(), 'badboy')
			actorOf(Child.props(), 'goodboy')
			// tell badboy to throw an exception
			scheduleIn(new StopMsg(), 1000, badboyRef)
		}

		@Override
		ActorBehavior createBehavior() {
			(messsage) -> {
				switch(messsage)
				{
					case ChildExceptionMsg -> {
						ChildExceptionMsg msg = (ChildExceptionMsg) messsage
						log.info "${msg.child} threw exception. Supervision=$supervisor  Will stop in 5 second"
						scheduleIn(new PoisonPill(), 5000)
					}
					default -> super.createBehavior().onMessage(messsage)
				}
			}
		}
	}

	@Slf4j
	static class Child extends Actor {

		static Props props() {
			return Props.create(Child)
		}

		@Override
		void preStart() {
			log.info "Starting child ${self.path}"
		}

		@Override
		void postStop() {
			if (! ActorSystem.isStopping)
				stopped = true
			log.info "${self.path} stopped"
		}

		@Override
		void preRestart(Throwable t) {
			restarted = true
			log.info "${self.path} restarted because of '$t'"
		}

		@Override
		void onResume() {
			resumed = true
			log.info "${self.path} resumed"
		}

		@Override
        ActorBehavior createBehavior() {
			(message) -> {
				switch(message)
				{
					case StopMsg -> throw new Exception("${self.path} threw an exception")
					default -> log.info "${self.path} got strange message %s"
				}
			}
		}
	}

	/*
	 Create three tests - one for each strategy (stop, resume or restart). Since mode is all for one the chosen
	 strategy will be applied to the failing Actor and its siblings
	 */
	def "Stop Supervision"() {
		when:
			log.info "Starting 'Stop' strategy"
			system.context.actorOf( Supervisor.props(SS_STOP, MODE_ALL_FOR_ONE), "supervisor").waitForDeath()
			system.stop()
			log.info "stopped=$stopped, resumed=$resumed, restarted=$restarted"
		then:
			! resumed
			! restarted
			stopped
	}

	def "Resume Supervision"() {
		when:
			log.info "Starting 'Resume' strategy"
			stopped = false; restarted = false; resumed = false
			system.context.actorOf( Supervisor.props(SS_RESUME, MODE_ALL_FOR_ONE), "supervisor").waitForDeath()
			system.stop()
			log.info "stopped=$stopped, resumed=$resumed, restarted=$restarted"
		then:
			! stopped
			! restarted
			resumed
	}

	def "Restart Supervision"() {
		when:
			log.info "Starting 'Restart' strategy"
			stopped = false; restarted = false; resumed = false
			system.context.actorOf( Supervisor.props(SS_RESTART, MODE_ALL_FOR_ONE), "supervisor").waitForDeath()
			system.stop()
			log.info "stopped=$stopped, resumed=$resumed, restarted=$restarted"
		then:
			! stopped
			! resumed
			restarted
	}
}
