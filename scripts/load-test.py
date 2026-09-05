#!/usr/bin/env python3
"""검색 API 부하 측정. 표준 라이브러리만 쓴다 — docs/load-test.md의 수치를 재현하는 도구.

사용:
  python3 scripts/load-test.py                          # 기본: 동시 40, 10초
  python3 scripts/load-test.py --clients 40 --duration 10
  python3 scripts/load-test.py --url http://localhost:8080/internal/mappings --clients 1
"""

import argparse
import statistics
import threading
import time
import urllib.request

DEFAULT_URL = ("http://localhost:8080/api/v1/stays/search"
               "?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0")


def worker(url, deadline, latencies, errors, lock):
    while time.monotonic() < deadline:
        started = time.monotonic()
        try:
            with urllib.request.urlopen(url, timeout=30) as res:
                res.read()
                ok = res.status == 200
        except Exception:
            ok = False
        elapsed_ms = (time.monotonic() - started) * 1000
        with lock:
            if ok:
                latencies.append(elapsed_ms)
            else:
                errors.append(elapsed_ms)


def percentile(values, p):
    return statistics.quantiles(values, n=100)[p - 1] if len(values) >= 2 else values[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default=DEFAULT_URL)
    parser.add_argument("--clients", type=int, default=40)
    parser.add_argument("--duration", type=float, default=10.0, help="측정 시간(초)")
    parser.add_argument("--warmup", type=float, default=2.0, help="버리는 워밍업 시간(초)")
    args = parser.parse_args()

    # 워밍업 — JIT·커넥션 풀이 덥혀지기 전 수치는 버린다
    warm_deadline = time.monotonic() + args.warmup
    warm = ([], [], threading.Lock())
    run(args.url, args.clients, warm_deadline, *warm)

    latencies, errors, lock = [], [], threading.Lock()
    deadline = time.monotonic() + args.duration
    started = time.monotonic()
    run(args.url, args.clients, deadline, latencies, errors, lock)
    elapsed = time.monotonic() - started

    total = len(latencies)
    print(f"url        : {args.url}")
    print(f"clients    : {args.clients}, duration {elapsed:.1f}s (warmup {args.warmup:.0f}s 제외)")
    print(f"requests   : {total} ok, {len(errors)} failed")
    if total:
        print(f"throughput : {total / elapsed:,.1f} req/s")
        print(f"latency    : p50 {percentile(latencies, 50):,.0f}ms"
              f" / p95 {percentile(latencies, 95):,.0f}ms"
              f" / max {max(latencies):,.0f}ms")


def run(url, clients, deadline, latencies, errors, lock):
    threads = [threading.Thread(target=worker, args=(url, deadline, latencies, errors, lock))
               for _ in range(clients)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()


if __name__ == "__main__":
    main()
