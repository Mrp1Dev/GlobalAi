type TranslationResult = {
    type: "result";
    translation: string;
};

type WorkerStatus =
    | {
        type: "loading";
    }
    | {
        type: "ready";
    }
    | TranslationResult
    | {
        type: "error";
        error: string;
    };

let workerInstance: Worker | null = null;
let busy = false;

function getWorker(): Worker {
    if (typeof Worker === "undefined") {
        throw new Error("Web Workers are not supported in this environment");
    }
    if (!workerInstance) {
        workerInstance = new Worker(
            new URL("../workers/translator.worker.ts", import.meta.url),
            {
                type: "module",
            }
        );
    }
    return workerInstance;
}

export function isTranslatorBusy(): boolean {
    return busy;
}

export interface TranslateOptions {
    onStatus?: (status: "loading" | "ready" | "translating") => void;
    forceMock?: boolean;
}

export function translateText(
    text: string,
    sourceLanguage: string,
    targetLanguage: string,
    options?: TranslateOptions
): Promise<string> {
    if (options?.forceMock) {
        return Promise.resolve(`[Translated (${sourceLanguage} -> ${targetLanguage})]: ${text}`);
    }

    const worker = getWorker();
    busy = true;
    return new Promise((resolve, reject) => {
        const handleMessage = (event: MessageEvent<WorkerStatus>) => {
            const data = event.data;

            if (data.type === "loading") {
                options?.onStatus?.("loading");
            } else if (data.type === "ready") {
                options?.onStatus?.("ready");
            } else if (data.type === "result") {
                busy = false;
                worker.removeEventListener("message", handleMessage);
                resolve(data.translation);
            } else if (data.type === "error") {
                busy = false;
                worker.removeEventListener("message", handleMessage);
                reject(new Error(data.error));
            }
        };

        worker.addEventListener("message", handleMessage);

        options?.onStatus?.("translating");

        worker.postMessage({
            text,
            sourceLanguage,
            targetLanguage,
        });
    });
}