import java.rmi.server.RemoteServer;
import java.util.HashMap;

//OVERVIEW: classe che rappresenta gli utenti registrati con le rispettive password

public class UserSet extends RemoteServer implements UserSetInterface {

    private HashMap<String,String> userMap;

    public UserSet(HashMap<String,String> hm){
        userMap= hm;
    }

    // Metodo che consente ad un client di registrarsi
    // restituisce true in caso di successo
    // lancia IllegalArgumentException() se la lunghezza di uno dei due parametri è 0
    // lancia UsernameAlreadyExistsException() se esiste già un utente con lo stesso username
    public synchronized boolean Register(String username,String password) throws UsernameAlreadyExistsException{
        if(username==null || password==null) throw new NullPointerException();
        if(username.length()==0 ||password.length()==0 ) throw new IllegalArgumentException();
        if(userMap.containsKey(username)) throw new UsernameAlreadyExistsException();
        userMap.put(username,password);
        return true;
    }
}
