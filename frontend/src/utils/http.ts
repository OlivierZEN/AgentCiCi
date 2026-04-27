export type ApiEnvelope<T = unknown> = {
  success?: boolean;
  message?: string;
  data?: T;
};

type SafeJsonResult<T = unknown> = {
  body: ApiEnvelope<T> | null;
  rawText: string;
};

export async function safeFetchJson<T = unknown>(res: Response): Promise<SafeJsonResult<T>> {
  const rawText = await res.text();
  if (!rawText.trim()) {
    return { body: null, rawText };
  }
  try {
    const parsed = JSON.parse(rawText) as ApiEnvelope<T>;
    return { body: parsed, rawText };
  } catch {
    return { body: null, rawText };
  }
}
