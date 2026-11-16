import { FetchEventSource } from './fetch-event-source';

function streamOf(chunks: string[], keepOpen = false): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      if (!keepOpen) {
        controller.close();
      }
    },
  });
  return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
}

describe('FetchEventSource', () => {
  let calls: { url: string; headers: Record<string, string> }[];

  beforeEach(() => {
    calls = [];
  });

  function stub(...responses: Response[]): void {
    spyOn(window, 'fetch').and.callFake((url, init) => {
      calls.push({ url: String(url), headers: (init?.headers ?? {}) as Record<string, string> });
      return Promise.resolve(responses[Math.min(calls.length, responses.length) - 1]);
    });
  }

  const wait = (ms = 20): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

  it('manda el token en Authorization y entrega cada evento a su oyente, aunque lleguen cortados', async () => {
    stub(streamOf(['id: 1\nevent: step\ndata: {"seq"', ':1}\n\nevent: end\ndata: {"id":"r1"}\n\n'], true));
    const source = new FetchEventSource('/api/runs/r1/events', async () => 'tok-9', 10_000);
    const got: string[] = [];
    source.addEventListener('step', (event) => got.push('step ' + event.data));
    source.addEventListener('end', (event) => got.push('end ' + event.data));
    await wait();

    expect(calls[0].headers['Authorization']).toBe('Bearer tok-9');
    expect(calls[0].url).toBe('/api/runs/r1/events');
    expect(got).toEqual(['step {"seq":1}', 'end {"id":"r1"}']);
    source.close();
  });

  it('si la conexion se corta, avisa por onerror y reconecta con Last-Event-ID', async () => {
    stub(streamOf(['id: 7\nevent: step\ndata: {}\n\n']), streamOf([], true));
    const source = new FetchEventSource('/api/runs/r1/events', async () => 'tok', 5);
    let errors = 0;
    source.onerror = () => errors++;
    source.addEventListener('step', () => undefined);
    await wait(80);

    expect(errors).toBeGreaterThanOrEqual(1);
    expect(calls.length).toBeGreaterThanOrEqual(2);
    expect(calls[1].headers['Last-Event-ID']).toBe('7');
    source.close();
  });

  it('close() corta la lectura y no reconecta', async () => {
    stub(streamOf([], true));
    const source = new FetchEventSource('/api/x', async () => null, 5);
    await wait();
    source.close();
    const before = calls.length;
    await wait(40);

    expect(source.readyState).toBe(2);
    expect(calls.length).toBe(before);
    expect(calls[0].headers['Authorization']).toBeUndefined();
  });
});
