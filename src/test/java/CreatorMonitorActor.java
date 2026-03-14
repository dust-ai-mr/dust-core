import com.mentalresonance.dust.core.actors.*;
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;

import java.io.Serializable;

/**
 * PArt of performance Tests
 */
public class CreatorMonitorActor extends Actor {

    Integer number, size;
    Long t1;
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
        t1 = System.currentTimeMillis();
        for (int i = 0; i < number; ++i) {
            actorOf(CreatorActor.props(size), "group-" + i);
        }
        try {
            Thread.sleep(5000);
        }
        catch (InterruptedException e) {}

        tellSelf(new PoisonPill());

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {}

        System.out.println("Stopping");
    }

    @Override
    public void postStop() {
        System.out.println("Stopped");
    }
}
