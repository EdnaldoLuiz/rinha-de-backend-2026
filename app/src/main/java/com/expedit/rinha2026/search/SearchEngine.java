package com.expedit.rinha2026.search;

import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;

public interface SearchEngine {
    SearchResult search(QueryVector queryVector);
}
