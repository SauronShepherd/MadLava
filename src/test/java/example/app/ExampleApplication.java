package example.app;

public final class ExampleApplication {
    public static void main(String[] args) throws Exception { long total=0; for(int i=0;i<10000;i++) total+=i;new ExampleApplication();new ObservedException("MADLAVA_PACKAGED_SECRET_91827");try{throw new ObservedException("MADLAVA_PACKAGED_SECRET_91827");}catch(ObservedException expected){}Thread.sleep(1200);System.out.println("MADLAVA_EXAMPLE_OK="+total); }
    static final class ObservedException extends RuntimeException { ObservedException(String message){super(message);} }
}
