package com.madlava.instrumentation;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** The sole primary transformer. It instruments constructors and Throwable flow. */
public final class CompositeTransformer implements ClassFileTransformer {
    private static final String BRIDGE = "com/madlava/probes/ProbeBridge";
    private final String includePrefix;

    public CompositeTransformer(String includePrefix) {
        this.includePrefix = includePrefix == null ? "" : includePrefix.replace('.', '/');
    }

    @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> redefining,
                                      ProtectionDomain domain, byte[] bytes) {
        if (name == null || bytes == null || name.startsWith("com/madlava/") || !name.startsWith(includePrefix)) return null;
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            reader.accept(new InstrumentingClassVisitor(writer, name), ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable ignored) { return null; }
    }

    private static final class InstrumentingClassVisitor extends ClassVisitor {
        private final String owner; private String superName;
        private InstrumentingClassVisitor(ClassVisitor delegate, String owner){super(Opcodes.ASM9,delegate);this.owner=owner;}
        @Override public void visit(int v,int a,String n,String s,String sn,String[] i){superName=sn;super.visit(v,a,n,s,sn,i);}
        @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
            MethodVisitor delegate=super.visitMethod(access,name,descriptor,signature,exceptions);
            if ((access & (Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE)) != 0) return delegate;
            return new InstrumentingMethodVisitor(delegate, owner, superName, name, descriptor);
        }
    }

    private static final class InstrumentingMethodVisitor extends MethodVisitor {
        private final String owner, superName, name; private final boolean constructor;
        private final Label start=new Label(), end=new Label(), handler=new Label(); private boolean initialized;
        private InstrumentingMethodVisitor(MethodVisitor mv,String owner,String superName,String name,String descriptor){
            super(Opcodes.ASM9,mv);this.owner=owner;this.superName=superName;this.name=name;this.constructor="<init>".equals(name);
        }
        @Override public void visitCode(){super.visitCode();if(!constructor)visitLabel(start);}
        @Override public void visitMethodInsn(int opcode,String calledOwner,String calledName,String descriptor,boolean itf){
            boolean initializing=constructor && !initialized && opcode==Opcodes.INVOKESPECIAL && "<init>".equals(calledName) && (calledOwner.equals(owner)||calledOwner.equals(superName));
            super.visitMethodInsn(opcode,calledOwner,calledName,descriptor,itf);
            if(initializing){initialized=true;visitVarInsn(Opcodes.ALOAD,0);visitLdcInsn(owner.replace('/','.'));invoke("constructorInitialized","(Ljava/lang/Object;Ljava/lang/String;)V");visitLabel(start);}
        }
        @Override public void visitInsn(int opcode){
            if(constructor && opcode==Opcodes.RETURN){visitVarInsn(Opcodes.ALOAD,0);invoke("constructorComplete","(Ljava/lang/Object;)V");}
            if(opcode==Opcodes.ATHROW){visitInsn(Opcodes.DUP);invoke("explicitThrow","(Ljava/lang/Throwable;)V");}
            super.visitInsn(opcode);
        }
        @Override public void visitMaxs(int maxStack,int maxLocals){
            visitLabel(end);visitTryCatchBlock(start,end,handler,"java/lang/Throwable");visitLabel(handler);
            visitInsn(Opcodes.DUP);invoke("propagated","(Ljava/lang/Throwable;)V");
            if(constructor)invoke("constructorFailed","()V");
            super.visitInsn(Opcodes.ATHROW);
            super.visitMaxs(maxStack,maxLocals);
        }
        private void invoke(String method,String descriptor){super.visitMethodInsn(Opcodes.INVOKESTATIC,BRIDGE,method,descriptor,false);}
    }
}
