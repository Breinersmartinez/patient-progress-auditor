import { EventSourceLike } from './runs.service';

// EventSource del navegador no deja mandar cabeceras, y el API exige "Authorization: Bearer". Este lector sigue el mismo
// protocolo SSE con fetch: lee el flujo, separa los eventos por linea en blanco y avisa a cada oyente por su nombre.
// Si la conexion se corta, avisa por onerror (RunsService consulta el estado) y reconecta con Last-Event-ID.
export class FetchEventSource implements EventSourceLike {
  readyState = 0; // 0 conectando, 1 abierto, 2 cerrado (como EventSource)
  onerror: ((event: Event) => void) | null = null;
  private readonly listeners: Record<string, (event: MessageEvent<string>) => void> = {};
  private readonly abort = new AbortController();
  private lastEventId = '';

  constructor(private readonly url: string, private readonly token: () => Promise<string | null>, private readonly retryMs = 3000) {
    void this.run();
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void {
    this.listeners[type] = listener;
  }

  close(): void {
    this.readyState = 2;
    this.abort.abort();
  }

  private async run(): Promise<void> {
    while (this.readyState !== 2) {
      try {
        const headers: Record<string, string> = { Accept: 'text/event-stream' };
        const token = await this.token();
        if (token) {
          headers['Authorization'] = `Bearer ${token}`;
        }
        if (this.lastEventId) {
          headers['Last-Event-ID'] = this.lastEventId;
        }
        const response = await fetch(this.url, { headers, signal: this.abort.signal });
        if (!response.ok || !response.body) {
          throw new Error(`SSE ${response.status}`);
        }
        this.readyState = 1;
        await this.read(response.body);
      } catch {
        if (this.readyState === 2) {
          return;
        }
      }
      this.readyState = 0;
      this.onerror?.(new Event('error'));
      await new Promise((resolve) => setTimeout(resolve, this.retryMs));
    }
  }

  private async read(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        return;
      }
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n?/g, '\n');
      let cut: number;
      while ((cut = buffer.indexOf('\n\n')) >= 0) {
        this.dispatch(buffer.slice(0, cut));
        buffer = buffer.slice(cut + 2);
      }
    }
  }

  private dispatch(block: string): void {
    let type = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith(':')) {
        continue;
      }
      const colon = line.indexOf(':');
      const field = colon < 0 ? line : line.slice(0, colon);
      const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');
      if (field === 'event') {
        type = value;
      } else if (field === 'data') {
        data.push(value);
      } else if (field === 'id') {
        this.lastEventId = value;
      }
    }
    if (data.length) {
      this.listeners[type]?.({ data: data.join('\n'), lastEventId: this.lastEventId } as MessageEvent<string>);
    }
  }
}
