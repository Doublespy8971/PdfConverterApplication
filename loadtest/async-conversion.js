import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const docxPath = __ENV.DOCX_FILE || 'loadtest/fixtures/small.docx';
const imagePath = __ENV.IMAGE_FILE || 'loadtest/fixtures/small.png';
const docxFile = open(docxPath, 'b');
const imageFile = open(imagePath, 'b');
const failures = new Counter('conversion_failures');
const rateLimited = new Counter('conversion_rate_limited');
const conversionDuration = new Trend('conversion_duration_ms');

export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '1m',
};

function convert(tool, path, filename, mimeType) {
  const started = Date.now();
  const response = http.post(
    `${baseUrl}/api/convert/${tool}`,
    { file: http.file(tool === 'word-to-pdf' ? docxFile : imageFile, filename, mimeType) },
  );

  if (response.status === 429) {
    rateLimited.add(1, { tool });
    return;
  }
  if (!check(response, { 'conversion accepted': (res) => res.status === 202 })) {
    failures.add(1, { tool, phase: 'submit' });
    return;
  }

  const taskId = response.json('taskId');
  let completed = false;
  for (let attempt = 0; attempt < 180; attempt += 1) {
    sleep(1);
    const statusResponse = http.get(`${baseUrl}/api/convert/status/${taskId}`);
    if (statusResponse.status === 429) {
      rateLimited.add(1, { tool });
      return;
    }
    if (!check(statusResponse, { 'status request succeeded': (res) => res.status === 200 })) {
      failures.add(1, { tool, phase: 'poll' });
      return;
    }
    const status = statusResponse.json('status');
    if (status === 'COMPLETED') {
      completed = true;
      break;
    }
    if (status === 'FAILED') {
      failures.add(1, { tool, phase: 'conversion' });
      return;
    }
  }

  if (!completed) {
    failures.add(1, { tool, phase: 'timeout' });
    return;
  }

  const downloadResponse = http.get(`${baseUrl}/api/convert/download/${taskId}`);
  if (downloadResponse.status === 429) {
    rateLimited.add(1, { tool });
    return;
  }
  if (!check(downloadResponse, { 'download succeeded': (res) => res.status === 200 })) {
    failures.add(1, { tool, phase: 'download' });
    return;
  }
  conversionDuration.add(Date.now() - started, { tool });
}

export default function () {
  convert('word-to-pdf', docxPath, 'small.docx',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
  convert('images-to-pdf', imagePath, 'small.png', 'image/png');
}
