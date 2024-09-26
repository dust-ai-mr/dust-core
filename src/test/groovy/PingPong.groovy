import com.mentalresonance.dust.core.actors.ActorRef
import com.mentalresonance.dust.core.msgs.PingMsg
import com.mentalresonance.dust.core.actors.ActorSystem
import com.mentalresonance.dust.core.actors.lib.PingActor
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
/**
 * Use the dust-core provide PingActor to bounce a limited number of messages back and forth.
 * Use two pairs of ping-pongers to process 4,000,000 messages in total
 */
class PingPong extends Specification {

	def "PingPonger"() {
		when:
			ActorSystem system = new ActorSystem("PingPong")

			log.info "Sending 40,000,000 messages ..."
			ActorRef ping2 = system.context.actorOf(PingActor.props(10000000), 'ping2')
			ActorRef pong2 = system.context.actorOf(PingActor.props(10000000), 'pong2')
			ActorRef ping3 = system.context.actorOf(PingActor.props(10000000), 'ping3')
			ActorRef pong3 = system.context.actorOf(PingActor.props(10000000), 'pong3')

			long ts = System.currentTimeMillis()
			ping2.tell(new PingMsg(), pong2)

			ping3.tell(new PingMsg(), pong3)

			ping2.waitForDeath()
			pong2.waitForDeath()
			ping3.waitForDeath()
			pong3.waitForDeath()

			def dt = (System.currentTimeMillis() - ts) / 1000f
			log.info "40,000,000 message in $dt seconds = ${40000000 / dt} messages / second"
			system.stop()
					then:
			true
	}

}
