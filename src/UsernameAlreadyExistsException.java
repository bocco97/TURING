class UsernameAlreadyExistsException extends Exception {
    public UsernameAlreadyExistsException(){
        super();
    }
    public UsernameAlreadyExistsException(String s){
        super(s);
    }
}
