import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * The interface of user list.
 *
 * @author Joonatan Kallio
 * Created on 2018-10-04
 */
public interface UserList {
    int MIN_UPDATE_BROADCAST_TIME = 500;
    int MAX_UPDATE_BROADCAST_TIME = 5000;

    /**
     * Add a user to this list distinctively (i.e. the same user is added only
     * once) and returns true, if the user was added as new and false
     * otherwise.
     *
     * @param user the user to add.
     * @return true, if the user was added as new and false otherwise.
     */
    boolean addDistinct(User user);

    /**
     * Add all the distinct users in the given list to this list. The users
     * that are already in this list, won't be added again.
     *
     * @param users the users to add.
     */
    void addAllDistinct(Iterable<User> users);

    /**
     * Remove a user from this list.
     *
     * @param user the user to remove.
     */
    void removeByName(User user);

    /**
     * Returns all the users as a collection.
     *
     * @return All the users in this list a collection.
     */
    Collection<User> asCollection();

    String toString();

    int getNumberOfUsers();

    /**
     * Schedules a list update task.
     *
     * @param task The task to schedule.
     */
    void scheduleListUpdate(Runnable task);

    /**
     * Cancels list update.
     */
    void cancelListUpdate();

    /**
     * Makes sure all timer related threads are closed.
     */
    void close();
//    /**
//     * A timer for list update broadcasts.
//     *
//     * @return timer instance.
//     */
//    Timer getListUpdateTimer();
}
