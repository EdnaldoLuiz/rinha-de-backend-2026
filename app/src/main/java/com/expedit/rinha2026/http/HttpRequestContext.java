package com.expedit.rinha2026.http;

import com.expedit.rinha2026.fallback.FallbackScorer;
import com.expedit.rinha2026.parser.PayloadParser;
import com.expedit.rinha2026.response.ResponseBuffers;
import com.expedit.rinha2026.response.ResponseWriter;
import com.expedit.rinha2026.search.SearchEngine;
import com.expedit.rinha2026.vectorizer.FraudVectorizer;

public record HttpRequestContext(
    PayloadParser parser,
    FraudVectorizer vectorizer,
    SearchEngine searchEngine,
    ResponseBuffers responseBuffers,
    ResponseWriter responseWriter,
    FallbackScorer fallbackScorer
) {
}
