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

class RemotePingPong extends Specification {

	ActorSystem me = new ActorSystem("me", 9094)  // I'm watching remote Actors so I need to be remote

	static ActorSystem system1 = new ActorSystem("RemotePingPong", 9095)
	static ActorSystem system2 = new ActorSystem("RemotePingPong", 9096)

	@Slf4j
	static class Runner extends Actor {

		ActorRef ping, pong

		int running = 2
		int PINGS = 1000000
		long started

		static Props props() {
			Props.create(Runner)
		}

		void preStart() {
			system1.context.actorOf(PingActor.props(PINGS), 'ping3')
			system2.context.actorOf(PingActor.props(PINGS), 'pong3')

			ping = watch(system1.context.actorSelection("dust://localhost:9095/RemotePingPong/user/ping3"))
			pong = watch(system2.context.actorSelection("dust://localhost:9096/RemotePingPong/user/pong3"))
			ping.tell(new PingMsg(), pong)
			started = System.currentTimeMillis()
		}

		ActorBehavior createBehavior() {
			(Serializable message) -> {
				switch(message) {
					case Terminated:
						if (--running == 0) {
							long deltaT = System.currentTimeMillis() - started
							log.info "Processed ${2*PINGS} msgs in $deltaT ms. ${(2000f * PINGS) / deltaT} msgs/sec"
							stopSelf()
						}
						break

					default:
						log.error "???"
				}
			}
		}
	}
	def "Remote Ping Ponger"() {
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
