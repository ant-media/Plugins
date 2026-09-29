package io.antmedia.plugin;

import java.lang.reflect.Field;

/**
 * Field access for the muxer/fetcher internals that FFmpeg natives keep out of reach of a
 * behavioural assertion. Shared so the test classes stop carrying their own copy.
 */
public final class TestReflect {

    private TestReflect() { }

    @SuppressWarnings("unchecked")
    public static <T> T getField(Object target, String name) throws Exception {
        return (T) field(target.getClass(), name).get(target);
    }

    public static void setField(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    public static int getInt(Object target, String name) throws Exception {
        return field(target.getClass(), name).getInt(target);
    }

    public static void setInt(Object target, String name, int value) throws Exception {
        field(target.getClass(), name).setInt(target, value);
    }

    public static boolean getBoolean(Object target, String name) throws Exception {
        return field(target.getClass(), name).getBoolean(target);
    }

    public static void setBoolean(Object target, String name, boolean value) throws Exception {
        field(target.getClass(), name).setBoolean(target, value);
    }

    // Statics are addressed by owning class. They wrap the checked exception because every call
    // site names a field that has to exist for the test to be meaningful at all.

    @SuppressWarnings("unchecked")
    public static <T> T staticField(Class<?> owner, String name) {
        try {
            return (T) field(owner, name).get(null);
        } catch (Exception e) {
            throw new IllegalStateException(owner.getName() + "." + name, e);
        }
    }

    public static void setStaticField(Class<?> owner, String name, Object value) {
        try {
            field(owner, name).set(null, value);
        } catch (Exception e) {
            throw new IllegalStateException(owner.getName() + "." + name, e);
        }
    }

    private static Field field(Class<?> start, String name) throws NoSuchFieldException {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // declared further up, keep walking
            }
        }
        throw new NoSuchFieldException(name);
    }
}
