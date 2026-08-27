let sdkPromise: Promise<void> | null = null;

export function loadTossPaymentsSdk() {
  if (window.TossPayments) {
    return Promise.resolve();
  }
  if (sdkPromise) {
    return sdkPromise;
  }

  sdkPromise = new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(
      'script[src="https://js.tosspayments.com/v2/standard"]',
    );
    const script = existing ?? document.createElement("script");

    const handleLoad = () => resolve();
    const handleError = () => {
      sdkPromise = null;
      reject(new Error("토스페이먼츠 결제창을 불러오지 못했습니다."));
    };
    script.addEventListener("load", handleLoad, { once: true });
    script.addEventListener("error", handleError, { once: true });

    if (!existing) {
      script.src = "https://js.tosspayments.com/v2/standard";
      script.async = true;
      document.head.appendChild(script);
    }
  });

  return sdkPromise;
}
