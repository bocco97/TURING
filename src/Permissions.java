import java.util.*;

//OVERVIEW: classe che raccoglie le informazioni utili alla gestione dei file nel TURINGserver

public class Permissions {
    //creatore del file
    private String owner;
    //lista degli utenti autorizzati
    private LinkedList<String> authorized;
    //lista degli utenti che stanno editando le sezioni
    private Vector<String> sections;
    //lista di booleani che indica se una sezione è in corso di aggiornamento in seguito ad una end-edit
    private  Vector<Boolean> updating;
    //indirizzo ip per la chat associato al documento
    private String address;

    public Permissions(String s, int nsections){
        if(s==null) throw new NullPointerException();
        if(nsections<=0) throw new IllegalArgumentException();
        owner=s;
        address=null;
        authorized=new LinkedList<>();
        sections = new Vector<>(nsections+1);
        for(int i=0; i<=nsections; i++) sections.add(null);
        updating = new Vector<>(nsections+1);
        for(int i=0; i<=nsections; i++) updating.add(false);
    }

    public String getOwner() {
        return owner;
    }

    public List<String> getAuthorized() {
        return Collections.unmodifiableList(authorized);
    }

    //restituisce il numero delle sezioni
    public int getNsections(){
        return sections.size()-1;
    }

    //aggiunge un utente alla lista degli utenti autorizzati
    public void add_authorization(String s){
        if(s==null) throw new NullPointerException();
        authorized.add(s);
    }

    //rimuove un utente dalla lista degli utenti autorizzati
    public void rm_authorization(String s){
        if(s==null) throw new NullPointerException();
        authorized.remove(s);
    }

    //restituisce true se l'utente è autorizzato, false altrimenti
    public boolean isAuthorized(String s){
        if(s==null) throw new NullPointerException();
        return s.equals(owner)||authorized.contains(s);
    }

    public String toString(){
        return "Owner: ".concat(owner).concat("\nCollaborators: ").concat(authorized.toString());
    }

    //tenta di bloccare una sezione del file (se nessun altro utente la sta già editando);
    //restituisce true in caso di successo, false altrimenti
    synchronized boolean lock_section(int i, String user){
        if(i<=0 || i>= sections.size()) throw new IllegalArgumentException();
        if(sections.get(i)!=null){ return false; }
        sections.set(i,user);
        return true;
    }

    //sblocca tutte le eventuali sezioni editate dall'utente usr
    synchronized void unlock_section(String usr){
        for(int i=1; i<sections.size(); i++){
            if(sections.get(i)!=null && sections.get(i).equals(usr)){
                sections.set(i,null);
            }
        }
    }

    //restituisce l'indice della sezione editata dall'utente user
    public int getSectionEditedBy(String user){
        return sections.indexOf(user);
    }

    //tenta di settare la variabile di aggiornamento della sezione i a true (se non è già in corso di aggiornamento);
    //restituisce true in caso di successo, false altrimenti;
    synchronized boolean update_section(int i){
        if(i<=0 || i>= updating.size()) throw new IllegalArgumentException();
        if(updating.get(i)) return false;
        updating.set(i,true);
        return true;
    }

    //setta la variabile di aggiornamento della sezione i a false
    synchronized void end_update_section(int i){
        if(i<=0 || i>= updating.size()) throw new IllegalArgumentException();
        updating.set(i,false);
    }

    //restituisce true se la sezione i è in corso di aggiornamento, false altrimenti
    public boolean isUpdating(int i){
        if(i<=0 || i>= updating.size()) return false;
        return updating.get(i);
    }

    //tenta di settare l'indirizzo s come indirizzo associato al file (se il file non aveva già un indirizzo associato):
    //restituisce true in caso di successo, false altrimenti;
    synchronized boolean setIpAddr(String s){
        if(address==null){
            address=s;
            return true;
        }
        return false;
    }

    //se nessun utente sta effettuando la edit sul documento, setta l'indirizzo associato al file a null e restituisce
    //l'indirizzo precedentemente associato; resituisce null altrimenti
    synchronized String revokeIpIfNotNeeded(){
        for(String s:sections){
            if(s!=null) return null;
        }
        String old_addr=address;
        address=null;
        return old_addr;
    }

    //restituisce l'indirizzo associato al file
    synchronized String getIpAddr(){
        return address;
    }
}
