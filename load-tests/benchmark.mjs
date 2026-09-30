import http from 'http';

/**
 * Node.js high-concurrency benchmark runner for URL Shortener redirects.
 * Calculates p50, p90, p95, p99 latencies, requests/second throughput,
 * and error percentages.
 * 
 * Usage: node load-tests/benchmark.mjs [concurrency] [totalRequests]
 */

const CONCURRENCY = parseInt(process.argv[2] || '30');
const TOTAL_REQUESTS = parseInt(process.argv[3] || '1000');
const HOST = process.env.TARGET_HOST || 'localhost';
const PORT = parseInt(process.env.TARGET_PORT || '8080');
const CODE = process.env.SHORT_CODE || 'bench123';

console.log(`\n======================================================`);
console.log(` Amazon ShortLink Benchmark (Node Native Concurrent) `);
console.log(` Target: http://${HOST}:${PORT}/${CODE}`);
console.log(` Concurrency: ${CONCURRENCY} | Total Requests: ${TOTAL_REQUESTS}`);
console.log(`======================================================\n`);

const latencies = [];
let completed = 0;
let errors = 0;
let started = Date.now();

function sendRequest(index) {
  return new Promise((resolve) => {
    const ipSuffix = (index % 250) + 1;
    const reqStart = process.hrtime.bigint();

    const req = http.request({
      hostname: HOST,
      port: PORT,
      path: `/${CODE}`,
      method: 'GET',
      headers: {
        'X-Forwarded-For': `10.0.2.${ipSuffix}`,
        'User-Agent': 'node-bench/1.0',
        'Referer': 'https://amazon.com'
      }
    }, (res) => {
      res.resume(); // Consume stream
      const reqEnd = process.hrtime.bigint();
      const latencyMs = Number(reqEnd - reqStart) / 1_000_000;
      latencies.push(latencyMs);

      if (res.statusCode !== 302) {
        errors++;
      }
      completed++;
      resolve();
    });

    req.on('error', () => {
      errors++;
      completed++;
      resolve();
    });

    req.end();
  });
}

async function runPool() {
  let index = 0;
  async function worker() {
    while (index < TOTAL_REQUESTS) {
      const current = index++;
      await sendRequest(current);
    }
  }

  const workers = Array.from({ length: CONCURRENCY }, () => worker());
  await Promise.all(workers);

  const totalTimeSeconds = (Date.now() - started) / 1000;
  latencies.sort((a, b) => a - b);

  function percentile(p) {
    const idx = Math.min(latencies.length - 1, Math.floor((p / 100) * latencies.length));
    return latencies[idx]?.toFixed(2) || '0.00';
  }

  const rps = (completed / totalTimeSeconds).toFixed(1);

  console.log(`Results:`);
  console.log(`------------------------------------------------------`);
  console.log(`Total Requests Sent : ${completed}`);
  console.log(`Total Errors        : ${errors} (${((errors / completed) * 100).toFixed(2)}%)`);
  console.log(`Throughput          : ${rps} req/sec`);
  console.log(`p50 Latency         : ${percentile(50)} ms`);
  console.log(`p90 Latency         : ${percentile(90)} ms`);
  console.log(`p95 Latency         : ${percentile(95)} ms`);
  console.log(`p99 Latency         : ${percentile(99)} ms`);
  console.log(`Max Latency         : ${latencies[latencies.length - 1]?.toFixed(2) || 0} ms`);
  console.log(`------------------------------------------------------\n`);
}

runPool();
