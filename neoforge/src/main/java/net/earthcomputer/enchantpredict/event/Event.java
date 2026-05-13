package net.earthcomputer.enchantpredict.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Lightweight, Fabric-style event holder. Replaces {@code net.fabricmc.fabric.api.event.Event}
 * so the ported enchantment prediction code keeps the same call-shape.
 */
public final class Event<T> {
    private final List<T> listeners = new ArrayList<>();
    private final Function<List<T>, T> invokerFactory;
    private T invoker;

    private Event(Function<List<T>, T> invokerFactory) {
        this.invokerFactory = invokerFactory;
        this.invoker = invokerFactory.apply(listeners);
    }

    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<List<T>, T> invokerFactory) {
        return new Event<>(invokerFactory);
    }

    public void register(T listener) {
        listeners.add(listener);
        invoker = invokerFactory.apply(listeners);
    }

    public T invoker() {
        return invoker;
    }
}
