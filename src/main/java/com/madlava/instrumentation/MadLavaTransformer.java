package com.madlava.instrumentation;

import com.madlava.methods.MethodFilter;
import com.madlava.methods.MethodKey;
import com.madlava.methods.MethodRegistry;
import com.madlava.serialization.SparkSerializationPlan;
import com.madlava.serialization.SparkSerializationTarget;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One deterministic transformer for generic method tracing and Spark serializer analysis.
 */
public final class MadLavaTransformer implements ClassFileTransformer {
    private static final String METHOD_BRIDGE = "com/madlava/methods/MethodProbeBridge";
    private static final String SERIALIZATION_BRIDGE = "com/madlava/serialization/SparkSerializationBridge";

    private final boolean methodProfilingEnabled;
    private final MethodFilter methodFilter;
    private final MethodRegistry methodRegistry;
    private final boolean sparkSerializationEnabled;
    private final SparkSerializationPlan serializationPlan;

    public MadLavaTransformer(
            boolean methodProfilingEnabled,
            MethodFilter methodFilter,
            MethodRegistry methodRegistry,
            boolean sparkSerializationEnabled,
            SparkSerializationPlan serializationPlan) {
        this.methodProfilingEnabled = methodProfilingEnabled;
        this.methodFilter = methodFilter;
        this.methodRegistry = methodRegistry;
        this.sparkSerializationEnabled = sparkSerializationEnabled;
        this.serializationPlan = serializationPlan;
    }

    @Override
    public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        // The probe bridge is application-loader code. Never inject calls into bootstrap
        // classes until a bootstrap-visible bridge is deliberately provisioned.
        if (loader == null || className == null || classfileBuffer == null || excluded(className) || !mayTransformClass(className)) {
            return null;
        }

        try {
            if (sparkSerializationEnabled) {
                serializationPlan.classVisited(className);
            }
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassNode classNode = new ClassNode(Opcodes.ASM9);
            reader.accept(classNode, 0);

            boolean changed = false;
            String owner = className.replace('/', '.');
            String loaderScope = loaderScope(loader);
            for (MethodNode method : classNode.methods) {
                if (!eligible(method)) {
                    continue;
                }

                boolean generic = methodProfilingEnabled
                        && methodFilter.matches(owner, method.name, method.desc);
                Optional<SparkSerializationTarget> target = sparkSerializationEnabled
                        ? serializationPlan.find(className, method.name, method.desc)
                        : Optional.empty();
                if (!generic && target.isEmpty()) {
                    continue;
                }

                int methodId = MethodRegistry.REJECTED_ID;
                if (generic) {
                    methodId = methodRegistry.register(new MethodKey(loaderScope, owner, method.name, method.desc));
                    generic = methodId != MethodRegistry.REJECTED_ID;
                }
                if (!generic && target.isEmpty()) {
                    continue;
                }
                if (target.isPresent()) {
                    serializationPlan.targetMatched(target.get().id());
                }

                instrument(method, methodId, generic, target.orElse(null));
                changed = true;
            }

            if (!changed) {
                return null;
            }
            SafeClassWriter writer = new SafeClassWriter(
                    reader,
                    ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
                    loader);
            classNode.accept(writer);
            if (sparkSerializationEnabled && serializationPlan.mayMatchClass(className)) {
                serializationPlan.classTransformed();
            }
            return writer.toByteArray();
        } catch (Throwable ignored) {
            if (sparkSerializationEnabled && serializationPlan.mayMatchClass(className)) {
                serializationPlan.transformationFailed();
            }
            return null;
        }
    }

    public boolean mayTransformClass(String internalName) {
        if (internalName == null || excluded(internalName)) {
            return false;
        }
        String owner = internalName.replace('/', '.');
        return (methodProfilingEnabled && methodFilter.mayMatchClass(owner))
                || (sparkSerializationEnabled && serializationPlan.mayMatchClass(internalName));
    }

    private static void instrument(
            MethodNode method,
            int methodId,
            boolean generic,
            SparkSerializationTarget serializationTarget) {
        List<AbstractInsnNode> originalReturns = new ArrayList<>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (isReturn(instruction.getOpcode())) {
                originalReturns.add(instruction);
            }
        }

        int nextLocal = method.maxLocals;
        int methodStartedLocal = -1;
        if (generic) {
            methodStartedLocal = nextLocal;
            nextLocal += 2;
        }
        int serializationTokenLocal = -1;
        if (serializationTarget != null) {
            serializationTokenLocal = nextLocal;
            nextLocal += 2;
        }
        int throwableLocal = nextLocal;
        nextLocal += 1;
        method.maxLocals = Math.max(method.maxLocals, nextLocal);

        LabelNode protectedStart = new LabelNode();
        LabelNode protectedEnd = new LabelNode();
        LabelNode handler = new LabelNode();

        InsnList entry = new InsnList();
        if (generic) {
            entry.add(new LdcInsnNode(methodId));
            entry.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    METHOD_BRIDGE,
                    "enter",
                    "(I)J",
                    false));
            entry.add(new VarInsnNode(Opcodes.LSTORE, methodStartedLocal));
        }
        if (serializationTarget != null) {
            entry.add(new LdcInsnNode(serializationTarget.id()));
            appendPrimaryArgument(entry, method.access, method.desc, serializationTarget.primaryArgumentIndex());
            entry.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    SERIALIZATION_BRIDGE,
                    "enter",
                    "(ILjava/lang/Object;)J",
                    false));
            entry.add(new VarInsnNode(Opcodes.LSTORE, serializationTokenLocal));
        }
        entry.add(protectedStart);
        method.instructions.insert(entry);

        for (AbstractInsnNode returnInstruction : originalReturns) {
            InsnList exit = new InsnList();
            if (serializationTarget != null) {
                if (returnInstruction.getOpcode() == Opcodes.ARETURN) {
                    exit.add(new InsnNode(Opcodes.DUP));
                } else {
                    exit.add(new InsnNode(Opcodes.ACONST_NULL));
                }
                exit.add(new VarInsnNode(Opcodes.LLOAD, serializationTokenLocal));
                exit.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        SERIALIZATION_BRIDGE,
                        "success",
                        "(Ljava/lang/Object;J)V",
                        false));
            }
            if (generic) {
                exit.add(new LdcInsnNode(methodId));
                exit.add(new VarInsnNode(Opcodes.LLOAD, methodStartedLocal));
                exit.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        METHOD_BRIDGE,
                        "normalExit",
                        "(IJ)V",
                        false));
            }
            method.instructions.insertBefore(returnInstruction, exit);
        }

        InsnList exceptionalExit = new InsnList();
        exceptionalExit.add(protectedEnd);
        exceptionalExit.add(handler);
        exceptionalExit.add(new VarInsnNode(Opcodes.ASTORE, throwableLocal));
        if (serializationTarget != null) {
            exceptionalExit.add(new VarInsnNode(Opcodes.ALOAD, throwableLocal));
            exceptionalExit.add(new VarInsnNode(Opcodes.LLOAD, serializationTokenLocal));
            exceptionalExit.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    SERIALIZATION_BRIDGE,
                    "failure",
                    "(Ljava/lang/Throwable;J)V",
                    false));
        }
        if (generic) {
            exceptionalExit.add(new LdcInsnNode(methodId));
            exceptionalExit.add(new VarInsnNode(Opcodes.LLOAD, methodStartedLocal));
            exceptionalExit.add(new VarInsnNode(Opcodes.ALOAD, throwableLocal));
            exceptionalExit.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    METHOD_BRIDGE,
                    "exceptionalExit",
                    "(IJLjava/lang/Throwable;)V",
                    false));
        }
        exceptionalExit.add(new VarInsnNode(Opcodes.ALOAD, throwableLocal));
        exceptionalExit.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(exceptionalExit);
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                protectedStart,
                protectedEnd,
                handler,
                "java/lang/Throwable"));
    }

    private static void appendPrimaryArgument(
            InsnList instructions,
            int access,
            String descriptor,
            int argumentIndex) {
        if (argumentIndex < 0) {
            instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            return;
        }
        Type[] arguments = Type.getArgumentTypes(descriptor);
        if (argumentIndex >= arguments.length) {
            instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            return;
        }
        int local = (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (int index = 0; index < argumentIndex; index++) {
            local += arguments[index].getSize();
        }
        Type argumentType = arguments[argumentIndex];
        if (argumentType.getSort() != Type.OBJECT && argumentType.getSort() != Type.ARRAY) {
            instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            return;
        }
        instructions.add(new VarInsnNode(Opcodes.ALOAD, local));
    }

    private static boolean eligible(MethodNode method) {
        if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) {
            return false;
        }
        return (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0;
    }

    private static boolean isReturn(int opcode) {
        return opcode == Opcodes.RETURN
                || opcode == Opcodes.IRETURN
                || opcode == Opcodes.LRETURN
                || opcode == Opcodes.FRETURN
                || opcode == Opcodes.DRETURN
                || opcode == Opcodes.ARETURN;
    }

    private static boolean excluded(String className) {
        return className.startsWith("com/madlava/")
                || className.startsWith("org/objectweb/asm/")
                || className.startsWith("com/madlava/internal/asm/")
                || "module-info".equals(className);
    }

    private static String loaderScope(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        return loader.getClass().getName()
                + '@'
                + Integer.toHexString(System.identityHashCode(loader));
    }
}
