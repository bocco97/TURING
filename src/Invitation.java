//OVERVIEW: classe che rappresenta l'invito di un utente per un determinato file

public class Invitation {
    private String from;
    private String file;

    public Invitation(String u, String f){
        if(u==null|| f==null ) throw new NullPointerException();
        from=u;
        file=f;
    }

    public String getFile() {
        return file;
    }

    public String getFrom() {
        return from;
    }

    @Override
    public String toString() {
        return from+" "+file;
    }
}
