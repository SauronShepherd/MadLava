package fixture.i03;

public final class ConstructorThrowableFixture {
    public static Object child() { return new Child(); }
    public static Object chained() { return new Chained(); }
    public static Object overloaded() { new Overloaded(); new Overloaded(1); return new Overloaded("x"); }
    public static Object innerAndAnonymous() { Outer outer=new Outer(); outer.new Inner(); return new Runnable(){public void run(){}}; }
    public static Object recursive() { return new Recursive(3); }
    public static void failedAfterInitialization() { new FailedAfterInitialization(); }
    public static Object createdOnly(String secret) { return new SecretException(secret); }
    public static void failedConstruction() { new FailedChild(); }
    public static void rethrowSame(Throwable value) throws Throwable {
        try { throw value; } catch (Throwable caught) { throw caught; }
    }
    public static void wrap(Throwable value, String secret) { throw new SecretException(secret, value); }

    public static final class Helper {}
    public static class Parent { Parent(Helper helper) {} }
    public static final class Child extends Parent { Child() { super(new Helper()); } }
    public static final class Chained {
        Chained() { this(1); }
        Chained(int ignored) {}
    }
    public static final class Overloaded { Overloaded(){} Overloaded(int value){} Overloaded(String value){} }
    public static final class Outer { final class Inner {} }
    public static final class Recursive { Recursive(int remaining){if(remaining>0)new Recursive(remaining-1);} }
    public static final class FailedAfterInitialization { FailedAfterInitialization(){throw new IllegalArgumentException("post-init-secret");} }
    public static class FailedParent { FailedParent() { throw new IllegalStateException("failed-super-secret"); } }
    public static final class FailedChild extends FailedParent { FailedChild() {} }
    public static final class SecretException extends RuntimeException {
        SecretException(String message) { super(message); }
        SecretException(String message, Throwable cause) { super(message, cause); }
    }
}
