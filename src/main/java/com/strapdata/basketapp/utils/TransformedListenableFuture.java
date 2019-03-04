package com.strapdata.basketapp.utils;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Apply a mapper function to the result of the provided listenable future.
 * @param <R>
 * @param <T>
 */
public class TransformedListenableFuture<R, T> implements ListenableFuture<T> {
    ListenableFuture<R> future;
    Function<? super R,? extends T> mapper;

    public TransformedListenableFuture(ListenableFuture<R> future, Function<? super R,? extends T> mapper) {
        this.future = future;
        this.mapper = mapper;
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        future.addListener(listener, executor);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return mapper.apply(future.get());
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return mapper.apply(future.get(timeout, unit));
    }
}
