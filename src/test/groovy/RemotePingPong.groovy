import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.msgs.PingMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.lib.PingActor
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
/**
 * Ping pong but as though between two networked Actors. We run two Actor systems on two ports thus
 * messages get (de)serialized during transmission.
 *
 * Note we cannot currently waitForDeath() on remoted Actors, so here we simply pause our test thread
 * to give things time to work through ... depending on the speed of your machine you may have to extend these sleep()s
 */
class RemotePingPong extends Specification {


	def "PingPonger"() {
		when:
			ActorSystem system1 = new ActorSystem("RemotePingPong", 9098)
			ActorSystem system2 = new ActorSystem("RemotePingPong2", 9099)
			log.info("Starting PingPong Warmup")

			system1.context.actorOf(PingActor.props(100000), 'ping2')
			system2.context.actorOf(PingActor.props(100000), 'pong2')
			ActorRef ping = system1.context.actorSelection("dust://localhost:9098/RemotePingPong/user/ping2")
			ActorRef pong = system2.context.actorSelection("dust://localhost:9099/RemotePingPong2/user/pong2")
			ping.tell(new PingMsg(), pong)
			sleep(10000L) // Adjust ???

			log.info "Warmed up - starting ping pong"
			system1.context.actorOf(PingActor.props(500000), 'ping3')
			system2.context.actorOf(PingActor.props(500000), 'pong3')
			ping = system1.context.actorSelection("dust://localhost:9098/RemotePingPong/user/ping3")
			pong = system2.context.actorSelection("dust://localhost:9099/RemotePingPong2/user/pong3")
			ping.tell(new PingMsg(), pong)
			sleep(30000L) // Adjust ??

			system1.stop()
			system2.stop()
					then:
			true
	}
}
