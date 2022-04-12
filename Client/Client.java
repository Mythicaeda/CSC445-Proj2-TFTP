import java.util.Scanner;

public class Client {

    private static final Scanner input = new Scanner(System.in);
    private static String host = null;
    private static int port = -1;
    private static boolean upload = false ; //default behavior is upload to server
    private static String fileName = null; //file to be uploaded
    private static boolean randDrops = false; //default behavior is no random drops
    private static int windowSize = -1;

    /**
     *@param args Expects either a hostname, a portnumber, a boolean, and a filename, or just a boolean and a filename
     */
    public static void main(String[] args){
        if(args.length < 1){
            System.err.print("Missing program arguments. ");
            erroredInput();
        }
        else if(args.length > 5){
            System.err.print("Too many program arguments. ");
            erroredInput();
        }
        else if (args.length == 2){
            if(!args[0].equalsIgnoreCase("true") || !args[0].equalsIgnoreCase("false")){
                System.err.print(args[0]+" is not a boolean. ");
                scuffedUpload();
            }
            else{
                upload = Boolean.parseBoolean(args[0]);
            }
            fileName = args[1];
        }
        else if (args.length == 4){
            host = args[0];
            try {
                port = Integer.parseInt(args[1]);
                if(port < 26920|| port >26929){
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException nfe) {
                System.err.print(args[1]+" is not a valid port number. ");
                scuffedPort();
            }
            if(!args[2].equalsIgnoreCase("true") || !args[2].equalsIgnoreCase("false")){
                System.err.print(args[2]+" is not a boolean. ");
                scuffedUpload();
            }
            else{
                upload = Boolean.parseBoolean(args[2]);
            }
            fileName = args[3];
        }
        else if (args.length == 5){
            host = args[0];
            try {
                port = Integer.parseInt(args[1]);
                if(port < 26920|| port >26929){
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException nfe) {
                System.err.print(args[1]+" is not a valid port number. ");
                scuffedPort();
            }
            if(!args[2].equalsIgnoreCase("true") || !args[2].equalsIgnoreCase("false")){
                System.err.print(args[2]+" is not a boolean. ");
                scuffedUpload();
            }
            else {
                upload = Boolean.parseBoolean(args[2]);
            }
            fileName = args[3];
        }
        else {
            System.err.println("Invalid number of program arguments");
            erroredInput();
        }
        getStarted();
    }


    private static void getStarted() {
        ClientUDP client;
        getRandDrops();
        getWindowSize();
        if(host != null) {
            client = new ClientUDP(host, port, upload, fileName, randDrops, windowSize);
        }
        else {
            client = new ClientUDP(upload, fileName, randDrops, windowSize);
        }
        client.open();
    }


    private static void erroredInput(){
        System.err.println("Use default host and port? y/n");
        char choice;
        do{
            choice = input.next().toLowerCase().charAt(0);
        }while (!(choice == 'y'|| choice == 'n'));
        if(choice == 'n'){
            System.err.println("Enter hostname: ");
            host = input.next();
            scuffedPort();
        }
        scuffedUpload();
        if(upload){
            System.err.println("Enter file to upload: ");
        }
        else{
            System.err.println("Enter file to download: ");
        }
        fileName = input.next();
    }


    private static void getWindowSize(){
        do{
            System.err.println("Enter the window size (an integer between 1 and 50):");
            try{
                windowSize = Integer.parseInt(input.next());}
            catch (NumberFormatException nfe){
                continue;
            }
        }while(windowSize < 1 || windowSize > 50);
    }

    private static void scuffedPort(){
        do{
            System.err.println("Enter a port number between 26920 and 26929:");
            try{
                port = Integer.parseInt(input.next());}
            catch (NumberFormatException nfe){
                continue;
            }
        }while(port < 26920 || port > 26929);
    }
    private static void scuffedUpload(){
        String choice;
        do{
            System.err.println("Enter either 'upload' or 'download' (without single quote) to indicate which mode to run. ");
            choice = input.next();
        }while(!(choice.equalsIgnoreCase("upload")||choice.equalsIgnoreCase("download")));
        if(choice.equalsIgnoreCase("upload")){
            upload = true;
        }
        else if(choice.equalsIgnoreCase("download")){
            upload = false;
        }
    }

    private static void getRandDrops(){
        System.err.println("Would you like to enable random drops? y/n");
        char choice;
        do{
            choice = input.next().toLowerCase().charAt(0);
        }while (!(choice == 'y'|| choice == 'n'));
        if(choice == 'y'){
            randDrops = true;
        }
    }
}
