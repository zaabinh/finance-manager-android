package com.example.fintrack.app.testing;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fake Retrofit services: every endpoint call is recorded and stays pending until the test answers it. */
public final class FakeApi {
    private final Map<String, List<StubCall<?>>> calls = new HashMap<>();
    private final Map<String, List<Object[]>> arguments = new HashMap<>();

    public <S> S create(Class<S> service) {
        return service.cast(Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[]{service}, (proxy, method, args) -> {
            StubCall<Object> call = new StubCall<>();
            calls.computeIfAbsent(method.getName(), name -> new ArrayList<>()).add(call);
            arguments.computeIfAbsent(method.getName(), name -> new ArrayList<>()).add(args);
            return call;
        }));
    }

    /** How often the endpoint (service method name) was called. */
    public int count(String endpoint) {
        return calls.containsKey(endpoint) ? calls.get(endpoint).size() : 0;
    }

    /** The index-th call of the endpoint, in call order. */
    @SuppressWarnings("unchecked")
    public <T> StubCall<T> call(String endpoint, int index) {
        return (StubCall<T>) calls.get(endpoint).get(index);
    }

    /** The request body of the index-th call of the endpoint. */
    @SuppressWarnings("unchecked")
    public <R> R body(String endpoint, int index) {
        return (R) arguments.get(endpoint).get(index)[0];
    }
}
