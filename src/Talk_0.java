import java.util.Scanner;

public class Talk_0 {

  @SuppressWarnings("unused")
  public static void main(String[] args) {
    Handler loopback = new LoopBackHandler();
    new Ticker(loopback, "somewhere", "Hello", 2500);
    new Ticker(loopback, "elsewhere", "Hi", 1000);
    Terminal myTalk = new Terminal(loopback, "third_place");
    Scanner sc = new Scanner(System.in);
    while (sc.hasNextLine()) {
      myTalk.send(sc.nextLine());
    }
    System.out.println("closing");
    sc.close();
    loopback.close();
  }
}
