import { pipeline } from "@huggingface/transformers";

let translator: any = null;

self.onmessage = async (event) => {
    const {
        text,
        sourceLanguage,
        targetLanguage,
    } = event.data;

    try {
        if (!translator) {
            self.postMessage({
                type: "loading",
            });

            translator = await pipeline(
                "translation",
                "Xenova/nllb-200-distilled-600M",
                {
                    dtype: "q8",
                }
            );

            self.postMessage({
                type: "ready",
            });
        }

        const result = await translator(text, {
            src_lang: sourceLanguage,
            tgt_lang: targetLanguage,
        });

        self.postMessage({
            type: "result",
            translation: result[0].translation_text,
        });
    } catch (error) {
        self.postMessage({
            type: "error",
            error: error instanceof Error
                ? error.message
                : "Unknown translation error",
        });
    }
};