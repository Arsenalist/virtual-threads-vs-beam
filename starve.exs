# Four requests hog a CPU for 10 seconds on 4 schedulers.
# A fifth, trivial request arrives once a second. How long does each one wait?
#   elixir --erl "+S 4" starve.exs
defmodule Starve do
  def hog(deadline, x) do
    if System.monotonic_time(:millisecond) < deadline, do: hog(deadline, x + 1), else: x
  end
end

IO.puts("elixir #{System.version()}, OTP #{System.otp_release()}, #{System.schedulers_online()} schedulers")

deadline = System.monotonic_time(:millisecond) + 10_000
for _ <- 1..4, do: spawn(fn -> Starve.hog(deadline, 0) end)
Process.sleep(100)

for n <- 1..5 do
  sent = System.monotonic_time(:microsecond)

  spawn(fn ->
    waited = System.monotonic_time(:microsecond) - sent
    IO.puts("request #{n} waited #{Float.round(waited / 1000, 2)} ms")
  end)

  Process.sleep(1000)
end

Process.sleep(5_000)
