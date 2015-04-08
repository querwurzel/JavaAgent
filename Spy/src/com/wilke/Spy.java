package com.wilke;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public final class Spy implements ClassFileTransformer {

    /**
     * A ClassFileTransformer is expected to transform existing classes during runtime.
     * This one is going to replace a whole class.
     * Therefore the original and substitute Information class share the same full qualified class name.
     * The Java language specification does not tell that the actual package name must match the real path.
     * Let's take advantage of that to load the substitute from some place different
     * as otherwise the same (the original) class would be loaded _again_.
     * To make this work the jar file has to be modified. The substitute class has to be moved (see substituteClassPath).
     */
    private static final String substituteClassPath = "Information.class"; // resides in the root of the jar file
    private static final byte[] substituteByteCode;

    /**
     * Internal (!) full qualified class name of the target class.
     * DO NOT: com.wilke.Information.class.getName().replace(".", "/") + ".class"
     * Classes used by Java agents cannot be transformed of said ones beforehand!
     */
    private static final String originalClassName = "com/wilke/Information";

    static {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(Spy.substituteClassPath)) {
            final byte[] buffer = new byte[1024];

            while (true) {
                int result = in.read(buffer);
                if (result == -1)
                    break;
                out.write(buffer, 0, result);
            }
        } catch (IOException io) {
            final IOException e = new IOException("Make sure to move the substitute Information class to the jar's root.", io);
            throw new ExceptionInInitializerError(e);
        } finally {
            substituteByteCode = out.toByteArray();
        }
    }

    /**
     * Java agent entry point when invoked by JVM parameter: -javaagent:MyAgent.jar
     * Relies on the 'Premain-Class' entry in the jar manifest.
     */
    public static void premain(final String agentArgument, final Instrumentation instrumentation) {
        instrumentation.addTransformer(new Spy());
    }

    /**
     * Java agent entry point when loaded during runtime.
     * Relies on the 'Agent-Class' entry in the jar manifest.
     */
//    public static void agentmain(final String agentArgument, final Instrumentation instrumentation) throws Exception {
//        final String nameOfRunningVM = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
//        final int pos = nameOfRunningVM.indexOf('@');
//        final String pid = nameOfRunningVM.substring(0, pos);
//
//        // requires the tools.jar of any JDK to be included in the class path
//        final com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach(pid);
//        final File file = new File("path/to/agent/jar/file.jar");
//        vm.loadAgent(file.getAbsolutePath());
//        vm.detach();
//    }

    @Override
    public byte[] transform(final ClassLoader loader, final String className,
                            final Class classBeingRedefined, final ProtectionDomain protectionDomain,
                            final byte[] classfileBuffer) throws IllegalClassFormatException {

        if (className.equals(Spy.originalClassName)) {
            System.out.println("Target class found, replacing by substitute");
            return Spy.substituteByteCode;
        }

        return null; // no transformation
    }
}
