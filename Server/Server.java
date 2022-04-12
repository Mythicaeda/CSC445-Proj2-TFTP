import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.io.File;

public class Server {
    private static final Scanner input = new Scanner(System.in);
    private static int port = -1;
    private static boolean randDrops = false;
    private static int windowSize = -1;

    /**
     *@param args Expects either nothing or just a port number (limited to my available ports for this project)
     */
    public static void main(String[] args){
        if(args.length > 1){
            System.err.print("Too many program arguments. ");
            erroredInput();
        }
        else if (args.length == 1){
            try {
                port = Integer.parseInt(args[0]);
                if(port < 26920|| port >26929){
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException nfe) {
                System.err.print(args[0]+" is not a valid port number. ");
                scuffedPort();
            }
        }
        else {
            erroredInput();
        }
        getStarted();
    }


    private static void getStarted() {
        ServerUDP server;
        getRandDrops();
        getWindowSize();
        if (port == -1) {
            server = new ServerUDP(randDrops, windowSize);
        } else {
            server = new ServerUDP(port, randDrops, windowSize);
        }
        server.open();
    }



    private static void erroredInput(){
        System.err.println("Use default port? y/n");
        char choice;
        do{
            choice = input.next().toLowerCase().charAt(0);
        }while (!(choice == 'y'|| choice == 'n'));
        if(choice == 'n'){
            scuffedPort();
        }
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
}
