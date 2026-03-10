import com.mentalresonance.dust.core.actors.Actor
import com.mentalresonance.dust.core.actors.ActorBehavior
import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.actors.Props
import com.mentalresonance.dust.core.msgs.PingMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.lib.PingActor
import com.mentalresonance.dust.core.msgs.Terminated
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
/**
 * Ping pong but as though between two networked Actors. We run two Actor systems on two ports thus
 * messages get (de)serialized during transmission.
 *
 * Note we cannot currently waitForDeath() on remoted Actors so here we watch them (which can watch remote Actors).
 * Since we have to serialize messages between fixed pairs of Actors (since the Dust Actor model guarantees message ordering)
 * there is a modest amount of overhead.
 *
 * We'll see in RemotePingPong that there is still headroom for multiple pairs of Actors.
 */

class RemotePingPong2 extends Specification {

	ActorSystem me = new ActorSystem("me", 9097)  // I'm watching remote Actors so I need to be remote
	static ActorSystem system1 = new ActorSystem("RemotePingPong2", 9098)
	static ActorSystem system2 = new ActorSystem("RemotePingPong2", 9099)

	@Slf4j
	static class Runner extends Actor {

		ActorRef ping3, pong3, ping4, pong4

		int running = 4
		int PINGS = 2000_000
		long started

		static Props props() {
			Props.create(Runner)
		}

		void preStart() {
			system1.context.actorOf(PingActor.props(PINGS), 'ping3')
			system2.context.actorOf(PingActor.props(PINGS), 'pong3')
			system1.context.actorOf(PingActor.props(PINGS), 'ping4')
			system2.context.actorOf(PingActor.props(PINGS), 'pong4')

			ping3 = watch(system1.context.actorSelection("dust://localhost:9098/RemotePingPong2/user/ping3"))
			pong3 = watch(system2.context.actorSelection("dust://localhost:9099/RemotePingPong2/user/pong3"))
			ping4 = watch(system1.context.actorSelection("dust://localhost:9098/RemotePingPong2/user/ping4"))
			pong4 = watch(system2.context.actorSelection("dust://localhost:9099/RemotePingPong2/user/pong4"))

			ping3.tell(new PingMsg(), pong3)
			ping4.tell(new PingMsg(), pong4)
			started = System.currentTimeMillis()
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case Terminated:
						if (--running == 0) {
							long deltaT = System.currentTimeMillis() - started
							log.info "Processed ${4*PINGS} msgs in $deltaT ms. ${(4000f * PINGS) / deltaT} msgs/sec"
							stopSelf()
						}
						break

					default:
						log.error "???"
				}
			}
		}
	}
	def "Remote Ping Ponger2"() {
		when:
		me.context.actorOf(Runner.props()).waitForDeath()
		me.stop()
		system1.stop()
		system2.stop()
		log.info "Finished"
	then:
		true
	}
}
