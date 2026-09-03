package com.madlava.instrumentation;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/** ClassWriter that resolves frames without initializing application classes. */
final class SafeClassWriter extends ClassWriter {
    private final ClassLoader loader;

    SafeClassWriter(ClassReader reader, int flags, ClassLoader loader) {
        super(reader, flags);
        this.loader = loader;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            ClassLoader effectiveLoader = loader == null
                    ? ClassLoader.getSystemClassLoader()
                    : loader;
            Class<?> first = Class.forName(type1.replace('/', '.'), false, effectiveLoader);
            Class<?> second = Class.forName(type2.replace('/', '.'), false, effectiveLoader);
            if (first.isAssignableFrom(second)) {
                return type1;
            }
            if (second.isAssignableFrom(first)) {
                return type2;
            }
            if (first.isInterface() || second.isInterface()) {
                return "java/lang/Object";
            }
            Class<?> candidate = first;
            do {
                candidate = candidate.getSuperclass();
            } while (candidate != null && !candidate.isAssignableFrom(second));
            return candidate == null ? "java/lang/Object" : candidate.getName().replace('.', '/');
        } catch (Throwable ignored) {
            return "java/lang/Object";
        }
    }
}
