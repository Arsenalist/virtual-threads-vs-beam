# Virtual threads vs BEAM processes

Two small demos of what one bad request does to the other requests on the same server, on Java virtual threads and on the Erlang VM (via Elixir). They go with the video *What Erlang does that Java virtual threads don't*.

Neither demo is a benchmark of speed. Each one asks a yes-or-no question about isolation.

## Setup

Versions used: JDK 27.0.1 (Temurin), Erlang/OTP 28, Elixir 1.20.4. With [mise](https://mise.jdx.dev):

```
mise install
```

## 1. The CPU: does a busy request make the others wait?

4 requests each use a CPU for 10 seconds, on a runtime given 4 cores. Then a request that does nothing arrives once a second. Each prints how long it waited before it started.

```
java -Djdk.virtualThreadScheduler.parallelism=4 -Djdk.virtualThreadScheduler.maxPoolSize=4 Starve.java virtual
java Starve.java platform
elixir --erl "+S 4" starve.exs
```

| Runtime | Wait for the first idle request |
|---|---|
| JDK 27, virtual threads, 4 carriers | 9,895 ms |
| JDK 27, one platform thread per request | 0 ms |
| Elixir 1.20.4 / OTP 28, 4 schedulers | 0.03 ms |

Why: a virtual thread leaves its carrier thread only when it blocks. [JEP 444](https://openjdk.org/jeps/444) says "The scheduler does not currently implement time sharing for virtual threads." Platform threads are time-sliced by the operating system, and the BEAM takes every process off its scheduler after 4,000 reductions, about a millisecond.

The platform-thread run was on an 18-core machine, so it also had spare cores.

## 2. The memory: who pays when one request uses it all?

One request allocates without limit in a 512 MB heap while 4 normal requests keep working (allocate 1 MB, sleep 10 ms, repeat).

```
java -Xmx512m Memory.java
java -XX:+UseZGC -Xmx512m Memory.java
elixir memory.exs
```

On Java, which request receives the `OutOfMemoryError` varies from run to run, so count over 20 runs:

```
./count-memory.sh
```

| Runtime | Runs in which a normal request was killed |
|---|---|
| JDK 27, G1 | 9 of 20 |
| JDK 27, ZGC | 11 of 20 |
| Elixir 1.20.4 / OTP 28, bad request capped at 512 MB | 0 of 5 |

Why: the JVM has one heap shared by all threads, and the error goes to whichever thread allocates next. A BEAM process has its own heap, and `max_heap_size` lets the runtime kill only the process that went over.

Two things this demo does not show. Java's garbage collectors never stalled a normal request for more than 30 ms here, so this is not about GC pauses. And the BEAM cap is something you set: without it, a runaway process takes the whole VM down too.

## License

MIT
