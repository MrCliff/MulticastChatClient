import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * An implementation of the {@link UserList} interface.
 *
 * @author Joonatan Kallio
 * Created on 2018-10-04
 */
public class UserListImpl implements UserList {
    private Collection<User> users = new LinkedList<>();

    private Timer listUpdateTimer = new Timer();
    private Random random = new Random();
    private TimerTask timerTask;
    private Runnable currentRunnable;

    /**
     * Add a user to this list distinctively (i.e. the same user is added only
     * once) and returns true, if the user was added as new and false
     * otherwise.
     *
     * @param user the user to add.
     * @return true, if the user was added as new and false otherwise.
     */
    @Override
    public boolean addDistinct(User user) {
        if (user == null) {
            return false;
        }

        boolean isUserAdded = users.stream().noneMatch(user::equalsByName);
        if (isUserAdded) {
            users.add(user);
        }
        return isUserAdded;
    }

    /**
     * Add all the users in the given list to this list.
     *
     * @param users the users to add.
     */
    @Override
    public void addAllDistinct(Iterable<User> users) {
        Collection<User> distinctUsers = StreamSupport.stream(users.spliterator(), false)
                .filter(Objects::nonNull)
                .filter(user -> this.users.stream().noneMatch(user::equalsByName))
                .collect(Collectors.toList());
        this.users.addAll(distinctUsers);
    }

    /**
     * Remove a user from this list.
     *
     * @param user the user to remove.
     */
    @Override
    public void removeByName(User user) {
        users.removeIf(user::equalsByName);
    }

    /**
     * Returns all the users as a collection.
     *
     * @return All the users in this list a collection.
     */
    @Override
    public Collection<User> asCollection() {
        return new ArrayList<>(users);
    }

    @Override
    public int getNumberOfUsers() {
        return users.size();
    }

    /**
     * Schedules a list update task.
     *
     * @param task The task to schedule.
     */
    @Override
    public void scheduleListUpdate(Runnable task) {
        if (currentRunnable != task) {
            cancelListUpdate();

            currentRunnable = task;
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    task.run();
                }
            };

            long delay = random.nextInt(MAX_UPDATE_BROADCAST_TIME - MIN_UPDATE_BROADCAST_TIME)
                    + MIN_UPDATE_BROADCAST_TIME;
            listUpdateTimer.schedule(timerTask, delay);
        }
    }

    /**
     * Cancels list update.
     */
    @Override
    public void cancelListUpdate() {
        if (timerTask != null) {
            timerTask.cancel();
            currentRunnable = null;
        }
    }

    /**
     * Makes sure all timer related threads are closed.
     */
    @Override
    public void close() {
        listUpdateTimer.cancel();
    }

    @Override
    public String toString() {
        return users.stream()
                .map(User::getUserName)
                .collect(Collectors.joining(", "));
    }
}
