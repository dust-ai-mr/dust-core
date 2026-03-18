import com.mentalresonance.dust.core.actors.*;
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;

/**
 * PArt of performance Tests
 */
public class CreatorMonitorActor extends Actor {

    Integer number, size;
    static Long tStart, tCreated, tStopped;
    Cancellable doit;

    public static Props props(Integer number, Integer size) {
        return Props.create(CreatorMonitorActor.class, number, size);
    }

    /**
     *
     * @param number - of CreatorActors to create. Each such actor will create ..
     * @param size  ... children then kill itself.
     *
     * When we have no children left we are done and we print out how long it took.
     * Note that the CreatorActors are deliberately not very efficient - they repeatedly send a message to themselves
     * to create their children so what we are measuring is
     *
     *              (number*size) Actor creations + (number*size) Messages to self
     *
     */
    public CreatorMonitorActor(Integer number, Integer size) {
        this.number = number;
        this.size = size;
    }

    @Override
    public void preStart() throws ActorInstantiationException {
        tStart = System.currentTimeMillis();
        for (int i = 0; i < number; ++i) {
            actorOf(CreatorActor.props(size), "group-" + i);
        }
        tCreated = System.currentTimeMillis();
        tellSelf(new PoisonPill());
    }

    public void postStop() {
        tStopped = System.currentTimeMillis();
    }
}
