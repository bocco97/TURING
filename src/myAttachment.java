//OVERVIEW: classe utilizzata dal TURINGserver per mantenere le associazioni tra le chiavi del selector e una
//          coppia username,valore

public class myAttachment {
    private String username;
    private String value;

    public myAttachment(String user, String v){
        if(user==null || v==null) throw new NullPointerException();
        username=user;
        value=v;
    }

    public String getUsername() {
        return username;
    }

    public String getValue() {
        return value;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
