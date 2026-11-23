import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 500 users hitting the endpoint at once, 5000 total attempts against 100 seats.
export const options = {
  vus: 500,
  iterations: 5000,
};

export default function () {
  const res = http.post(`${BASE_URL}/book`);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
