import java.util.HashMap;

//OVERVIEW: Classe che si occupa della generazione, distribuzione e raccoglimento di indirizzi IP di multicast.

public class ChatAddressesGenerator {
    private String from;
    private String to;
    private String actual;
    private HashMap<String,Boolean> addresses;

    public ChatAddressesGenerator(){
        from = "224:0:0:0";
        to = "239:255:255:255";
        addresses=new HashMap<>();
        actual = from;
    }

    //restituisce un indirizzo IP di multicast tra quelli disponibili, null in caso non ci sia un indirizzo libero
    synchronized String getNext(){
        if(!actual.equals(to)){
            String toReturn = actual;
            addresses.put(toReturn,false);
            actual=inc(actual);
            return toReturn;
        }
        for(String add: addresses.keySet()){
            if(addresses.get(add)){
                addresses.put(add,false);
                return add;
            }
        }
        return null;
    }

    //aggiunge un indirizzo IP (preso come parametro) a quelli disponibili
    synchronized void returnAddress(String add){
        if(add==null) return;
        if(addresses.containsKey(add)) addresses.put(add,true);
    }

    //restituisce l'indirizzo IP successivo ad un indirizzo preso come parametro
    private static String inc(String add){
        String [] bytes = add.split(":");
        if(!bytes[3].equals("255")){
            int b = Integer.parseInt(bytes[3]);
            b++;
            return bytes[0]+":"+bytes[1]+":"+bytes[2]+":"+b;
        }
        if(!bytes[2].equals("255")){
            int b = Integer.parseInt(bytes[2]);
            b++;
            return bytes[0]+":"+bytes[1]+":"+b+":"+"0";
        }
        if(!bytes[1].equals("255")){
            int b = Integer.parseInt(bytes[1]);
            b++;
            return bytes[0]+":"+b+":"+"0"+":"+"0";
        }
        else{
            int b = Integer.parseInt(bytes[0]);
            b++;
            return b+":"+"0"+":"+"0"+":"+"0";
        }
    }
}
