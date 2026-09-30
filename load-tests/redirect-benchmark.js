import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics for latency percentiles and error tracking
export const errorRate = new Rate('errors');
export const redirectDuration = new Trend('redirect_duration', true);

export const options = {
  stages: [
    { duration: '15s', target: 20 },  // Ramp-up to 20 virtual users
    { duration: '30s', target: 50 },  // Steady load at 50 virtual users
    { duration: '15s', target: 0 },   // Ramp-down
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],    // Error rate must be under 1%
    'redirect_duration': ['p(95)<15'], // p95 latency must be under 15ms
    'redirect_duration': ['p(99)<30'], // p99 latency must be under 30ms
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';
const SHORT_CODE = __ENV.SHORT_CODE || 'bench123';

export function setup() {
  // Pre-seed a test URL before the benchmark begins
  const payload = JSON.stringify({
    url: 'https://aws.amazon.com/architecture',
    customAlias: SHORT_CODE,
    ttlSeconds: 86400,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/shorten`, payload, params);
  console.log(`Setup: Seeded short code '${SHORT_CODE}' with status ${res.status}`);
  return { code: SHORT_CODE };
}

export default function (data) {
  const url = `${BASE_URL}/${data.code}`;
  
  // Custom headers to simulate varied clients and avoid IP rate limiting block
  const vuId = __VU;
  const ipSuffix = (vuId % 200) + 1;
  const params = {
    redirects: 0, // Do NOT follow redirect so we measure pure 302 response time
    headers: {
      'User-Agent': 'k6-load-tester/1.0',
      'Referer': 'https://internal.amazon.com/wiki',
      'X-Forwarded-For': `10.0.1.${ipSuffix}`,
    },
  };

  const start = Date.now();
  const res = http.get(url, params);
  const duration = Date.now() - start;

  redirectDuration.add(duration);

  const passed = check(res, {
    'status is 302': (r) => r.status === 302,
    'has location header': (r) => r.headers['Location'] !== undefined,
  });

  errorRate.add(!passed);
  sleep(0.05); // 50ms think time between requests
}
