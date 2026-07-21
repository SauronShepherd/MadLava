package example.app;

public final class ExampleApplication {
    public static void main(String[] args) throws Exception { long total=0; for(int i=0;i<10000;i++) total+=i; Thread.sleep(1200); System.out.println("MADLAVA_EXAMPLE_OK="+total); }
}
