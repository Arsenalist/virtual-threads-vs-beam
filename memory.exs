# One request keeps allocating and never lets go. Four normal requests keep working
# (each loop: allocate 1 MB, sleep 10 ms). The bad one runs under a 512 MB cap.
#   elixir memory.exs
defmodule Mem do
  def normal(n, parent, last, worst) do
    receive do
      :stop -> send(parent, {:done, n, worst})
    after
      0 ->
        _work = :binary.copy(<<1>>, 1_048_576) |> :binary.bin_to_list() |> length()
        Process.sleep(10)
        now = System.monotonic_time(:millisecond)
        normal(n, parent, now, max(worst, now - last))
    end
  end

  def hog(kept) do
    Process.sleep(1)
    hog([:lists.duplicate(131_072, 0) | kept])   # ~1 MB of list on this process's own heap
  end
end

IO.puts("elixir #{System.version()}, OTP #{System.otp_release()}, bad request capped at 512 MB")
parent = self()
t0 = System.monotonic_time(:millisecond)

normals = for n <- 1..4, do: spawn(fn -> Mem.normal(n, parent, System.monotonic_time(:millisecond), 0) end)
Process.sleep(500)

words = div(512 * 1_048_576, :erlang.system_info(:wordsize))
{pid, ref} =
  :erlang.spawn_opt(fn -> Mem.hog([]) end, [:monitor, max_heap_size: %{size: words, kill: true, error_logger: false}])

receive do
  {:DOWN, ^ref, :process, ^pid, reason} ->
    IO.puts("bad request #{inspect(reason)} by the runtime at #{(System.monotonic_time(:millisecond) - t0) / 1000} s")
after
  15_000 -> IO.puts("bad request still alive")
end

Process.sleep(max(0, 15_500 - (System.monotonic_time(:millisecond) - t0)))
for p <- normals, do: send(p, :stop)
for _ <- normals do
  receive do
    {:done, n, worst} -> IO.puts("normal request #{n} finished, worst stall #{worst} ms")
  after
    2000 -> IO.puts("a normal request did not answer")
  end
end
