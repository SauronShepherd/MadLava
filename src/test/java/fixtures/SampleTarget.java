package fixtures;

public class SampleTarget {
    private int touched;

    public int add(int left, int right) {
        return left + right;
    }

    public long widen(long value) {
        return value * 2L;
    }

    public float scale(float value) {
        return value * 1.5f;
    }

    public double ratio(double value) {
        return value / 2.0d;
    }

    public String echo(String value) {
        return "echo:" + value;
    }

    public void touch() {
        touched++;
    }

    public int touched() {
        return touched;
    }

    public int catchesInternally(boolean fail) {
        try {
            if (fail) {
                throw new IllegalStateException("internal-only");
            }
            return 1;
        } catch (IllegalStateException ignored) {
            return 2;
        }
    }

    public int failWith(RuntimeException failure) {
        throw failure;
    }

    public int recursive(int value) {
        return value <= 0 ? 0 : 1 + recursive(value - 1);
    }

    public synchronized int synchronizedMethod(int value) {
        return value + 1;
    }

    public int overloaded(int value) {
        return value + 10;
    }

    public String overloaded(String value) {
        return value + "!";
    }
}
