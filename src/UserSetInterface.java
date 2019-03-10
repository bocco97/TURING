import java.rmi.Remote;

public interface UserSetInterface extends Remote {
    public boolean Register(String username,String password) throws java.lang.Exception;
}
