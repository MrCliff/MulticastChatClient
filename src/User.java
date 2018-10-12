import java.time.LocalDate;

/**
 * Represents one chat user.
 *
 * @author Joonatan Kallio
 * Created on 2018-10-04
 */
public class User {
    private final String userName;
    private final LocalDate birthDate;

    /**
     * Initializes new user without birth date.
     *
     * @param userName the username of this user.
     */
    public User(String userName) {
        this.userName = userName;
        this.birthDate = null;
    }

    /**
     * Initializes new user.
     *
     * @param userName  the username of this user.
     * @param birthDate the birth date of this user.
     */
    public User(String userName, LocalDate birthDate) {
        this.userName = userName;
        this.birthDate = birthDate;
    }

    public String getUserName() {
        return userName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    @Override
    public int hashCode() {
        return userName.hashCode()
                + (birthDate != null ? birthDate.hashCode() : 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof User)) {
            return false;
        }
        User other = (User)obj;
        return userName.equals(other.userName)
                && (birthDate == null ? other.birthDate == null : birthDate.equals(other.birthDate));
    }

    /**
     * Determines, if the given user has the same name as this user.
     *
     * @param user the user to check.
     * @return true, if the given user has the same name as this user.
     */
    public boolean equalsByName(User user) {
        return userName.equals(user.userName);
    }
}
