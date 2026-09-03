package com.madlava.verification.support;

import com.madlava.instrumentation.MadLavaTransformer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Child-first class loader used to execute bytecode transformed by the real agent transformer. */
public final class TransformingClassLoader extends ClassLoader {
    private final MadLavaTransformer transformer;
    private final Map<String, byte[]> definitions = new HashMap<>();
    private final String childFirstPrefix;

    public TransformingClassLoader(
            MadLavaTransformer transformer,
            List<String> classNames,
            String childFirstPrefix) throws Exception {
        super(TransformingClassLoader.class.getClassLoader());
        this.transformer = transformer;
        this.childFirstPrefix = childFirstPrefix;
        for (String className : classNames) {
            definitions.put(className, resourceBytes(className));
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null && name.startsWith(childFirstPrefix) && definitions.containsKey(name)) {
                loaded = findClass(name);
            }
            if (loaded == null) {
                loaded = super.loadClass(name, false);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] original = definitions.get(name);
        if (original == null) {
            throw new ClassNotFoundException(name);
        }
        try {
            String internalName = name.replace('.', '/');
            byte[] transformed = transformer.transform(null, this, internalName, null, null, original);
            byte[] selected = transformed == null ? original : transformed;
            return defineClass(name, selected, 0, selected.length);
        } catch (Throwable failure) {
            throw new ClassNotFoundException("Unable to transform " + name, failure);
        }
    }

    private static byte[] resourceBytes(String className) throws Exception {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream input = TransformingClassLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing class resource: " + resource);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4_096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
